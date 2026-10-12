"""Data integrity check, stage C: what the datalogger publishes == what the backend stores == what the API returns.

For about a minute it records every telemetry / battery message of one device at the broker, the live snapshot
of the API at the same time, and afterwards the rows the backend wrote. Every field of every sample is compared.

    SMOKE_SSH="<host key>;root@<server>" SMOKE_SSH_PW=... python tools/verify_chain.py --serial <SN> --out result.json

Needs: plink on PATH, the stack in /opt/ggpower on the server, LOG_OTP_CODES=true (a temporary account is created
and removed again). Nothing is written to the device.
"""
import argparse
import json
import os
import random
import re
import secrets
import subprocess
import sys
import threading
import time

import requests

ARGS = None


def ssh(cmd, timeout=180):
    key, target = os.environ["SMOKE_SSH"].split(";", 1)
    return subprocess.run(["plink", "-batch", "-ssh", "-hostkey", key, "-pw", os.environ["SMOKE_SSH_PW"], target, cmd],
                          capture_output=True, text=True, encoding="utf-8", timeout=timeout).stdout


def sql(statement):
    return ssh(f'cd /opt/ggpower && docker compose exec -T db psql -U ggpower -d ggpower -Atc "{statement}"').strip()


# payload path -> (database column, API field, factor from payload to stored value)
TELEMETRY = [
    ("inv_status", "inv_status", "inv_status", 1), ("inv_temp", "inv_temp", "inv_temp_c", 1),
    ("pv.v_pv1", "pv1_v", "pv1_v", 1), ("pv.i_pv1", "pv1_a", "pv1_a", 1), ("pv.p_pv1", "pv1_w", "pv1_w", 1),
    ("pv.v_pv2", "pv2_v", "pv2_v", 1), ("pv.i_pv2", "pv2_a", "pv2_a", 1), ("pv.p_pv2", "pv2_w", "pv2_w", 1),
    ("pv.p_total", "pv_w", "pv_w", 1),
    ("grid.v_ac", "grid_v", "grid_v", 1), ("grid.i_ac", "grid_a", "grid_a", 1), ("grid.freq", "grid_hz", "grid_hz", 1),
    ("grid.p_active", "grid_w", "grid_w", 1),
    ("load.v_out", "load_v", "load_v", 1), ("load.i_out", "load_a", "load_a", 1), ("load.freq", "load_hz", "load_hz", 1),
    ("load.p_active", "load_w", "load_w", 1),
    # the app's sign convention for the battery is the opposite of the datalogger's (+ = charging)
    ("battery.p_flow", "batt_w", "battery_w", -1), ("battery.soc", "soc", "soc_percent", 1),
    ("energy.yield_today_kwh", "e_yield_today", "yield_today_kwh", 1), ("energy.yield_total_kwh", "e_yield_total", "yield_total_kwh", 1),
    ("energy.load_today_kwh", "e_load_today", "load_today_kwh", 1), ("energy.grid_export_today_kwh", "e_export_today", "export_today_kwh", 1),
    ("energy.grid_import_today_kwh", "e_import_today", "import_today_kwh", 1),
]
BATTERY = [
    ("soc", "soc", "soc_percent", 1), ("soh", "soh", "soh_percent", 1), ("voltage", "voltage", "voltage_v", 1),
    ("current", "current_a", "current_a", -1), ("power_w", "power_w", "power_w", 1),
    ("temp_cells_avg", "temp_cells", "temp_cells_c", 1), ("temp_bms", "temp_bms", "temp_bms_c", 1),
    ("cycle_count", "cycles", "cycles", 1), ("capacity_ah_remaining", "cap_remaining_ah", "capacity_remaining_ah", 1),
    ("capacity_ah_nominal", "cap_nominal_ah", "capacity_nominal_ah", 1), ("est_time_remaining_min", "est_minutes", "est_minutes", 1),
]


def dig(obj, path):
    for key in path.split("."):
        if not isinstance(obj, dict) or key not in obj:
            return None
        obj = obj[key]
    return obj


def same(expected, got):
    """Stored values are 32-bit floats: equal within float precision, never a rounding of the value itself."""
    if expected is None or got is None:
        return expected is None and got is None
    if isinstance(expected, bool) or isinstance(got, bool):
        return expected == got
    return abs(float(expected) - float(got)) <= max(1e-4, abs(float(expected)) * 2e-6)


def epoch(iso):
    from datetime import datetime
    return int(datetime.fromisoformat(iso.replace("Z", "+00:00")).timestamp())


def main():
    global ARGS
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--api", default=os.environ.get("SMOKE_BASE", "http://localhost:5080").rstrip("/") + "/api/v1",
                    help="API root; default: $SMOKE_BASE/api/v1")
    ap.add_argument("--seconds", type=int, default=60)
    ap.add_argument("--out", default="verify_chain.json")
    ARGS = ap.parse_args()
    base, serial = ARGS.api, ARGS.serial

    # ---- temporary read-only account that is a member of the device's project
    phone = "09" + "".join(random.choice("0123456789") for _ in range(8))
    email = f"viewtest.{phone}@example.com"
    password = "Vv" + secrets.token_hex(6) + "9a"
    assert requests.post(base + "/auth/register", json={"name": "Verify", "email": email, "phone": phone, "password": password}).status_code == 202
    code = re.findall(rf"DEV OTP for {phone} \(register\): (\d{{6}})", ssh("cd /opt/ggpower && docker compose --profile multi logs --since 2m api api2 2>&1 | grep 'DEV OTP'"))[-1]
    token = requests.post(base + "/auth/register/verify", json={"phone": phone, "code": code}).json()["access_token"]
    headers = {"Authorization": "Bearer " + token}
    result = {"serial": serial, "started_utc": time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime()), "seconds": ARGS.seconds}
    try:
        sql(f"insert into project_members (project_id, user_id) select pd.project_id, u.id from project_devices pd, users u where pd.serial = '{serial}' and u.email = '{email}'")
        project = requests.get(base + "/projects", headers=headers).json()[0]["id"]

        # ---- record the API snapshot while the broker is being recorded
        snapshots, stop = [], threading.Event()

        def poll():
            while not stop.is_set():
                try:
                    r = requests.get(f"{base}/projects/{project}/telemetry", headers=headers, timeout=5)
                    if r.status_code == 200:
                        snapshots.append(r.json())
                except requests.RequestException:
                    pass
                stop.wait(1.0)

        thread = threading.Thread(target=poll, daemon=True)
        thread.start()
        raw = ssh(f"/root/.ggp_mqtt.sh sub '+' 200 {ARGS.seconds}", timeout=ARGS.seconds + 60)
        time.sleep(4)
        stop.set()
        thread.join()

        telemetry, battery = [], []
        for line in raw.splitlines():
            topic, _, body = line.partition(" ")
            if not body.startswith("{"):
                continue
            if topic.endswith("/up/telemetry"):
                telemetry.append(json.loads(body))
            elif topic.endswith("/up/battery"):
                battery.append(json.loads(body))
        # retained copies replayed at subscribe time are older than the window: keep the samples by time
        start = int(time.time()) - ARGS.seconds - 30
        telemetry = [m for m in telemetry if m.get("ts", 0) >= start]
        battery = [m for m in battery if m.get("ts", 0) >= start]

        def rows(table, stamps):
            if not stamps:
                return {}
            out = sql(f"select row_to_json(t) from (select *, extract(epoch from ts)::bigint as epoch from {table} where serial = '{serial}' and ts in ("
                      + ",".join(f"to_timestamp({s})" for s in stamps) + ")) t")
            return {r["epoch"]: r for r in (json.loads(l) for l in out.splitlines() if l.startswith("{"))}

        db_t = rows("telemetry_raw", [m["ts"] for m in telemetry])
        db_b = rows("battery_raw", [m["ts"] for m in battery])
        api_t = {epoch(s["telemetry"]["ts"]): s["telemetry"] for s in snapshots if s.get("telemetry")}
        api_b = {epoch(s["battery"]["ts"]): s["battery"] for s in snapshots if s.get("battery")}

        checks, mismatches = 0, []
        fields = {}

        def compare(kind, messages, mapping, db, api, extra):
            nonlocal checks
            for m in messages:
                ts = m["ts"]
                row, snap = db.get(ts), api.get(ts)
                if row is None:
                    mismatches.append({"kind": kind, "ts": ts, "field": "(row)", "problem": "sample not found in the database"})
                    continue
                for path, column, api_field, factor in mapping + extra:
                    sent = dig(m, path)
                    expected = None if sent is None else (sent if isinstance(sent, (bool, str)) else sent * factor)
                    stat = fields.setdefault(f"{kind}.{path}", {"samples": 0, "db_ok": 0, "api_seen": 0, "api_ok": 0, "example": None})
                    stat["samples"] += 1
                    checks += 1
                    stored = row.get(column)
                    ok = stored == expected if isinstance(expected, str) else same(expected, stored)
                    stat["db_ok"] += ok
                    if not ok:
                        mismatches.append({"kind": kind, "ts": ts, "field": path, "sent": sent, "expected": expected, "database": stored})
                    if snap is not None and api_field is not None:
                        stat["api_seen"] += 1
                        checks += 1
                        shown = snap.get(api_field)
                        ok_api = shown == expected if isinstance(expected, str) else same(expected, shown)
                        stat["api_ok"] += ok_api
                        if not ok_api:
                            mismatches.append({"kind": kind, "ts": ts, "field": path, "sent": sent, "expected": expected, "api": shown})
                    if stat["example"] is None:
                        stat["example"] = {"ts": ts, "sent": sent, "database": stored, "api": None if snap is None or api_field is None else snap.get(api_field)}

        compare("telemetry", telemetry, TELEMETRY, db_t, api_t, [("battery.present", "batt_present", None, 1)])
        compare("battery", battery, BATTERY, db_b, api_b, [("present", "present", "present", 1), ("bms_comm", "bms_comm", "bms_comm", 1)])

        result.update({
            "telemetry_messages": len(telemetry), "battery_messages": len(battery),
            "telemetry_rows_found": len(db_t), "battery_rows_found": len(db_b),
            "api_snapshots": len(snapshots), "api_samples_matched": len(set(api_t) & {m["ts"] for m in telemetry}) + len(set(api_b) & {m["ts"] for m in battery}),
            "comparisons": checks, "mismatches": mismatches, "fields": fields,
            "last_telemetry": telemetry[-1] if telemetry else None, "last_battery": battery[-1] if battery else None,
            "last_snapshot": snapshots[-1] if snapshots else None,
        })
    finally:
        sql("delete from users where email like 'viewtest.%@example.com'")
        result["temp_account_removed"] = sql("select count(*) from users where email like 'viewtest.%'") == "0"
    json.dump(result, open(ARGS.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("telemetry %d msgs / %d rows, battery %d msgs / %d rows, API matched %d samples" % (
        result.get("telemetry_messages", 0), result.get("telemetry_rows_found", 0), result.get("battery_messages", 0),
        result.get("battery_rows_found", 0), result.get("api_samples_matched", 0)))
    print("comparisons: %d, mismatches: %d" % (result.get("comparisons", 0), len(result.get("mismatches", []))))
    for m in result.get("mismatches", [])[:15]:
        print("  MISMATCH", m)
    return 0


if __name__ == "__main__":
    sys.exit(main())
