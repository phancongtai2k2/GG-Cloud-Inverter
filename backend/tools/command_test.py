"""Configuration-command and alert flows against SIMULATED devices only (tenant "dev").

Needs the backend running, and two simulators on the same broker:
    python device_simulator.py --sn SIM-0001
    python device_simulator.py --sn SIM-LOCK --locked
Never point this at a real inverter: it changes operating modes and schedules.
"""
import json, os, ssl, subprocess, sys, time, uuid
import requests
import paho.mqtt.client as mqtt
from smoke_test import API, H, check, finish, new_account

DEV = os.path.join(os.environ["LOCALAPPDATA"], "ggpower-dev")
SECRETS = dict(l.strip().split("=", 1) for l in open(os.path.join(DEV, "secrets.env")) if "=" in l and not l.startswith("#"))
OK, LOCKED = "SIM-0001", "SIM-LOCK"


def sql(statement):
    env = {**os.environ, "PGPASSWORD": SECRETS["PG_APP_PASSWORD"]}
    r = subprocess.run([os.path.join(DEV, "pgsql", "bin", "psql.exe"), "-h", "localhost", "-p", "5433", "-U", "ggpower", "-d", "ggpower", "-Atc", statement],
                       capture_output=True, text=True, env=env)
    assert r.returncode == 0, r.stderr
    return r.stdout.strip()


def config(tok, serial):
    r = requests.get(f"{API}/devices/{serial}/config", headers=H(tok))
    assert r.status_code == 200, r.text
    return r.json()


def send(tok, serial, body, key=None):
    return requests.post(f"{API}/devices/{serial}/config-commands", headers={**H(tok), "Idempotency-Key": key or str(uuid.uuid4())}, json=body)


def wait_final(tok, serial, cmd_id, timeout=45):
    t0 = time.time()
    while time.time() - t0 < timeout:
        c = requests.get(f"{API}/devices/{serial}/config-commands/{cmd_id}", headers=H(tok)).json()
        if c["status"] in ("applied", "reverted", "failed"):
            return c, time.time() - t0
        time.sleep(0.5)
    return c, timeout


def run(tok, serial, body):
    """Send with the current version, wait for the final state."""
    time.sleep(5.2)   # one command per 5 s per device
    r = send(tok, serial, {**body, "based_on_version": config(tok, serial)["version"]})
    if r.status_code != 202:
        return r, None, 0
    c, took = wait_final(tok, serial, r.json()["command_id"])
    return r, c, took


def slot(h1, h2, soc=90, w=3000):
    return {"start": {"hour": h1, "minute": 0}, "end": {"hour": h2, "minute": 0}, "stop_soc_percent": soc, "max_power_w": w}


def main():
    for s in (OK, LOCKED):
        assert sql(f"select tenant from devices where serial = '{s}'") == "dev", f"{s} is not a simulated device on tenant dev"
    sql(f"delete from projects where id in (select project_id from project_devices where serial in ('{OK}', '{LOCKED}'))")
    sql(f"delete from config_allowlist where serial in ('{OK}', '{LOCKED}')")
    sql(f"delete from tou_strategies where serial in ('{OK}', '{LOCKED}')")
    sql("update app_settings set value = '{\"enabled\": true}' where key = 'config_write_switch'")

    _, email, owner = new_account("owner")
    _, admin_email, admin = new_account("admin")
    sql(f"update users set role = 'ggp_technician', write_admin = true where email = '{admin_email}'")
    admin = requests.post(API + "/auth/login", json={"account": admin_email, "password": "matkhau123"}).json()

    pids = {}
    for s in (OK, LOCKED):
        r = requests.post(API + "/projects", headers=H(owner), json={
            "serial": s, "name": "Sim " + s, "site_type": "home", "address": "Test", "installed_on": "2026-01-01", "pv_kwp": 5})
        assert r.status_code == 201, r.text
        pids[s] = r.json()["project_id"]

    c0 = config(owner, OK)
    print("      initial:", c0["mode"], c0["write_access"])
    check("simulated device starts read-only", c0["write_access"]["state"] == "device_read_only")
    r = send(owner, OK, {"group": "operating_mode", "based_on_version": c0["version"], "mode": "self_consumption"})
    check("write refused while read-only", r.status_code == 409 and r.json()["code"] == "DEVICE_READ_ONLY", r.text)

    for s in (OK, LOCKED):
        r = requests.put(f"{API}/admin/config-write-allowlist/{s}", headers=H(admin), json={"note": "simulator"})
        check(f"admin allows writes to {s}", r.status_code == 200, r.text)
    check("owner cannot use admin endpoints", requests.put(f"{API}/admin/config-write-allowlist/{OK}", headers=H(owner), json={}).status_code == 403)
    c1 = config(owner, OK)
    check("write access enabled", c1["write_access"]["state"] == "enabled" and "operating_mode" in c1["write_access"]["writable_groups"], str(c1["write_access"]))

    # --- operating mode ---
    target = "self_consumption" if c1["mode"] != "self_consumption" else "battery_priority"
    r, c, took = run(owner, OK, {"group": "operating_mode", "mode": target})
    check(f"mode -> {target} applied", c is not None and c["status"] == "applied" and c["outcome"]["device_state"] == "new", r.text if c is None else json.dumps(c))
    print(f"      applied in {took:.1f} s; changes: {c and c.get('changes')}")
    check("config reflects the read-back value", config(owner, OK)["mode"] == target)

    r = send(owner, OK, {"group": "operating_mode", "based_on_version": "v-00000000", "mode": "battery_priority"})
    check("stale version -> 409 CONFIG_STALE", r.status_code == 409 and r.json()["code"] == "CONFIG_STALE" and "current_version" in r.json(), r.text)
    r = send(owner, OK, {"group": "operating_mode", "based_on_version": config(owner, OK)["version"], "mode": "max_grid_export"})
    check("owner may not select grid export", r.status_code == 403 and r.json()["code"] == "ROLE_NOT_PERMITTED", r.text)
    r = send(owner, OK, {"group": "backup_soc", "based_on_version": config(owner, OK)["version"], "soc_percent": 30})
    check("backup SOC is not writable yet", r.status_code == 403 and r.json()["code"] == "GROUP_NOT_IN_ALLOWLIST", r.text)

    # --- idempotency and rate limit ---
    time.sleep(5.2)
    key = str(uuid.uuid4())
    other = "battery_priority" if target == "self_consumption" else "self_consumption"
    body = {"group": "operating_mode", "based_on_version": config(owner, OK)["version"], "mode": other}
    a = send(owner, OK, body, key)
    b = send(owner, OK, body, key)
    check("same idempotency key returns the same command", a.status_code == 202 and b.status_code == 202 and a.json()["command_id"] == b.json()["command_id"], a.text + b.text)
    d = send(owner, OK, {**body, "mode": target}, key)
    check("same key, different body -> 422", d.status_code == 422 and d.json()["code"] == "IDEMPOTENCY_KEY_REUSED", d.text)
    e = send(owner, OK, body)
    check("second command at once is refused", e.status_code in (409, 429) and e.json()["code"] in ("COMMAND_IN_PROGRESS", "RATE_LIMITED", "CONFIG_STALE"), e.text)
    c, _ = wait_final(owner, OK, a.json()["command_id"])
    check("that command applied once", c["status"] == "applied", json.dumps(c))

    # --- TOU ---
    sid = str(uuid.uuid4())
    days = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
    strat = {"id": sid, "name": "Hằng ngày", "weekdays": days, "charge_slots": [slot(22, 4), slot(12, 14, 80, 1500)], "discharge_slots": [slot(18, 21, 20, 5000)]}
    bad = {**strat, "charge_slots": [slot(22, 4), slot(3, 5)]}
    time.sleep(5.2)
    r = send(owner, OK, {"group": "tou_schedule", "based_on_version": config(owner, OK)["version"], "strategies": [bad]})
    check("overlapping slots -> 400 SLOT_OVERLAP", r.status_code == 400 and any(x["code"] == "SLOT_OVERLAP" for x in r.json().get("errors", [])), r.text)

    r, c, _ = run(owner, OK, {"group": "tou_schedule", "strategies": [strat]})
    check("schedule saved without writing (not in TOU)", c is not None and c["status"] == "applied" and c["outcome"]["device_written"] is False, r.text if c is None else json.dumps(c["outcome"]))
    check("unsupported discharge slots are reported", c is not None and "discharge_slots" in c["outcome"].get("unsupported", []))

    r, c, took = run(owner, OK, {"group": "operating_mode", "mode": "tou"})
    check("mode -> tou applied", c is not None and c["status"] == "applied", r.text if c is None else json.dumps(c))
    cfg = config(owner, OK)
    check("config shows TOU with today's strategy", cfg["mode"] == "tou" and cfg["today_strategy_id"] == sid, f"{cfg['mode']} {cfg['today_strategy_id']}")

    strat2 = {**strat, "charge_slots": [slot(23, 5, 95, 2500)]}
    r, c, took = run(owner, OK, {"group": "tou_schedule", "strategies": [strat2]})
    check("schedule change written while in TOU", c is not None and c["status"] == "applied" and c["outcome"]["device_written"] is True, r.text if c is None else json.dumps(c))
    state = json.loads(sql(f"select config_state::text from devices where serial = '{OK}'"))
    p1 = state["charge_schedule"]["periods"][0]
    check("device holds the new slot", (p1["start_time"], p1["end_time"], p1["charge_power_w"], p1["stop_soc"]) == ("23:00", "05:00", 2500, 95), str(p1))
    check("...and the removed slot is cleared", state["charge_schedule"]["periods"][1]["start_time"] == state["charge_schedule"]["periods"][1]["end_time"] == "00:00")

    r, c, _ = run(owner, OK, {"group": "operating_mode", "mode": "self_consumption"})
    check("leaving TOU switches the schedule off", c is not None and c["status"] == "applied" and config(owner, OK)["mode"] == "self_consumption", r.text)

    # --- a device that refuses the value (locked inverter) ---
    lock_cfg = config(owner, LOCKED)
    r, c, took = run(owner, LOCKED, {"group": "operating_mode", "mode": "self_consumption" if lock_cfg["mode"] != "self_consumption" else "tou"})
    if lock_cfg["mode"] == "battery_priority":
        check("refused value -> failed, device unchanged", c is not None and c["status"] == "failed" and c["failure"]["code"] == "MODBUS_EXCEPTION"
              and c["outcome"]["device_state"] == "old_unchanged", r.text if c is None else json.dumps(c))
        check("config still shows the old mode", config(owner, LOCKED)["mode"] == "battery_priority")
    else:
        print("SKIP locked-device check: simulator not in its initial mode")

    # --- emergency switch ---
    r = requests.put(API + "/admin/config-write-switch", headers=H(admin), json={"enabled": False, "reason": "test"})
    check("switch off", r.status_code == 200 and r.json()["enabled"] is False, r.text)
    check("config reports disabled_by_server", config(owner, OK)["write_access"]["state"] == "disabled_by_server")
    time.sleep(5.2)
    r = send(owner, OK, {"group": "operating_mode", "based_on_version": config(owner, OK)["version"], "mode": "battery_priority"})
    check("write refused while switched off", r.status_code == 409 and r.json()["code"] == "WRITE_DISABLED_BY_SERVER", r.text)
    requests.put(API + "/admin/config-write-switch", headers=H(admin), json={"enabled": True})

    r = requests.get(f"{API}/devices/{OK}/config-commands?limit=3", headers=H(owner))
    check("change history, newest first, paged", r.status_code == 200 and len(r.json()["items"]) == 3 and r.json()["next_cursor"], r.text[:200])
    check("history shows who did it", r.json()["items"][0]["initiator"].get("display_name") == "Smoke owner", str(r.json()["items"][0]["initiator"]))

    # --- alerts (published as the simulated device) ---
    m = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="cmdtest-" + uuid.uuid4().hex[:8])
    m.username_pw_set(SECRETS["MQTT_USER"], SECRETS["MQTT_PASSWORD"])
    m.tls_set(cert_reqs=ssl.CERT_REQUIRED)
    m.connect(SECRETS["MQTT_HOST"], int(SECRETS.get("MQTT_PORT", 8883)), 30)
    m.loop_start()
    time.sleep(2)
    alarm_id = "ALM-TEST-" + uuid.uuid4().hex[:6]
    topic = f"solar/dev/inverter/{OK}/up/alarm"
    alarm = {"alarm_id": alarm_id, "sn": OK, "ts": int(time.time()), "event_state": "triggered", "error_code": "F024", "severity": "critical",
             "title": "Mất kết nối lưới điện", "description": "Inverter chuyển sang cấp điện từ pin.", "data_snapshot": {"grid_v": 0}}
    m.publish(topic, json.dumps(alarm), qos=1).wait_for_publish(5)
    time.sleep(2)
    P = f"{API}/projects/{pids[OK]}"
    alerts = requests.get(P + "/alerts", headers=H(owner)).json()
    mine = [a for a in alerts if a["code"] == "F024" and a["ended_at_utc"] is None]
    check("alarm appears as an open error", len(mine) == 1 and mine[0]["severity"] == "error", str(alerts[:2]))
    ov = requests.get(P + "/overview", headers=H(owner)).json()
    check("project status turns to error", ov["status"] == "error" and ov["unresolved_alerts"] >= 1, str(ov)[:200])
    r = requests.get(f"{P}/alerts/{mine[0]['id']}", headers=H(owner))
    check("alert detail", r.status_code == 200 and r.json()["code"] == "F024" and r.json()["device"], r.text[:300])
    # a restarted datalogger reports the same fault again under a new id: still one open alert
    m.publish(topic, json.dumps({**alarm, "alarm_id": alarm_id + "-B", "ts": int(time.time())}), qos=1).wait_for_publish(5)
    time.sleep(2)
    again = [a for a in requests.get(P + "/alerts", headers=H(owner)).json() if a["code"] == "F024" and a["ended_at_utc"] is None]
    check("repeated report of an open fault is not a second alert", len(again) == 1, str(len(again)))
    m.publish(topic, json.dumps({**alarm, "event_state": "cleared", "ts": int(time.time())}), qos=1).wait_for_publish(5)
    time.sleep(2)
    after = [a for a in requests.get(P + "/alerts", headers=H(owner)).json() if a["id"] == mine[0]["id"]][0]
    check("cleared alarm is closed", after["ended_at_utc"] is not None, str(after))
    check("status back to online", requests.get(P + "/overview", headers=H(owner)).json()["status"] == "online")
    r = requests.put(f"{P}/alerts/{mine[0]['id']}/resolved", headers=H(owner), json={"resolved": True})
    check("mark resolved", r.status_code == 204 and requests.get(P + "/overview", headers=H(owner)).json()["unresolved_alerts"] == 0, r.text)
    # the device's daily counter restarted: an old id comes back for a new event and must not be lost
    time.sleep(1.2)
    m.publish(topic, json.dumps({**alarm, "ts": int(time.time())}), qos=1).wait_for_publish(5)
    time.sleep(2)
    f024 = [a for a in requests.get(P + "/alerts", headers=H(owner)).json() if a["code"] == "F024"]
    check("reused alarm id for a new event is stored as a new alert", len(f024) == 2 and sum(a["ended_at_utc"] is None for a in f024) == 1, str(len(f024)))
    m.publish(topic, json.dumps({**alarm, "event_state": "cleared", "ts": int(time.time())}), qos=1).wait_for_publish(5)
    time.sleep(1)
    m.loop_stop()

    # --- support ticket ---
    r = requests.post(API + "/tickets", headers=H(owner), json={"type": "technical", "title": "Thử", "description": "Mô tả", "project_id": pids[OK], "alert_id": mine[0]["id"]})
    check("create ticket", r.status_code == 201 and r.json()["code"].startswith("#HT-"), r.text)
    tid = r.json()["id"]
    r = requests.post(f"{API}/tickets/{tid}/messages", headers=H(owner), json={"text": "Xin chào"})
    check("ticket message", r.status_code == 201, r.text)
    png = bytes.fromhex("89504e470d0a1a0a") + b"\x00" * 64
    r = requests.post(f"{API}/tickets/{tid}/attachments", headers=H(owner), data=png)
    check("image attachment accepted", r.status_code == 201 and r.json()["content_type"] == "image/png", r.text)
    r = requests.post(f"{API}/tickets/{tid}/attachments", headers=H(owner), data=b"MZ\x90\x00 this is not an image")
    check("non-media attachment rejected", r.status_code == 415, r.text)
    det = requests.get(f"{API}/tickets/{tid}", headers=H(owner)).json()
    check("ticket detail", len(det["messages"]) == 1 and len(det["attachments"]) == 1, str(det)[:200])
    check("ticket is private to its author", requests.get(f"{API}/tickets/{tid}", headers=H(admin)).status_code == 404)

    # --- support staff ---
    A = API + "/admin/tickets"
    check("owner cannot use staff endpoints", requests.get(A, headers=H(owner)).status_code == 403)
    r = requests.get(A + "?status=received", headers=H(admin))
    check("staff lists new tickets", r.status_code == 200 and any(x["ticket"]["id"] == tid for x in r.json()), r.text[:200])
    r = requests.post(f"{A}/{tid}/messages", headers=H(admin), json={"text": "GG Power đã nhận yêu cầu."})
    check("staff replies", r.status_code == 201, r.text)
    det = requests.get(f"{API}/tickets/{tid}", headers=H(owner)).json()
    check("user sees the reply and the ticket is in progress", det["messages"][-1]["author"] == "support" and det["summary"]["status"] == "processing", str(det["summary"]))
    check("staff resolves", requests.put(f"{A}/{tid}/status", headers=H(admin), json={"status": "resolved"}).status_code == 204)
    requests.post(f"{API}/tickets/{tid}/messages", headers=H(owner), json={"text": "Vẫn còn lỗi"})
    check("a new user message reopens a resolved ticket", requests.get(f"{API}/tickets/{tid}", headers=H(owner)).json()["summary"]["status"] == "processing")
    requests.put(f"{A}/{tid}/status", headers=H(admin), json={"status": "closed"})
    r = requests.post(f"{API}/tickets/{tid}/messages", headers=H(owner), json={"text": "x"})
    check("closed ticket takes no more messages", r.status_code == 409 and r.json()["code"] == "TICKET_CLOSED", r.text)

    # --- removing a project frees its device ---
    check("another account cannot delete the project", requests.delete(f"{API}/projects/{pids[LOCKED]}", headers=H(admin)).status_code == 404)
    check("owner deletes the project", requests.delete(f"{API}/projects/{pids[LOCKED]}", headers=H(owner)).status_code == 204)
    check("deleted project is gone", requests.get(f"{API}/projects/{pids[LOCKED]}/overview", headers=H(owner)).status_code == 404)
    r = requests.post(API + "/projects/check-serial", headers=H(admin), json={"serial": LOCKED})
    check("its device can be added again", r.status_code == 200, r.text)

    sql("delete from users where email like 'smoke.%@example.com'")
    sql(f"delete from config_allowlist where serial in ('{OK}', '{LOCKED}')")
    return finish()


if __name__ == "__main__":
    sys.exit(main())
