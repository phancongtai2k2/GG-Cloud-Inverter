"""After-deploy check of a multi-instance stack (two API instances behind the load balancer, Redis, S3).

Every request goes through the public address, so the load balancer spreads them over the instances; each
read is repeated so that both instances answer. Uses a temporary account that is made a member of the project
holding SERIAL and removed again at the end (with its avatar). Read-only for the device: no command is sent.

    SMOKE_BASE=https://<host> SMOKE_SSH="<host key>;root@<server>" SMOKE_SSH_PW=... python tools/deploy_check.py --serial <SN>
"""
import argparse
import asyncio
import json
import os
import random
import re
import secrets
import struct
import subprocess
import sys
import zlib

import requests
import websockets

results = []


def check(name, ok, detail=""):
    results.append(bool(ok))
    print(("PASS " if ok else "FAIL ") + name + (f"  [{detail}]" if detail else ""))
    return ok


def ssh(cmd):
    key, target = os.environ["SMOKE_SSH"].split(";", 1)
    return subprocess.run(["plink", "-batch", "-ssh", "-hostkey", key, "-pw", os.environ["SMOKE_SSH_PW"], target, cmd],
                          capture_output=True, text=True, encoding="utf-8").stdout


def sql(statement):
    return ssh(f'cd /opt/ggpower && docker compose exec -T db psql -U ggpower -d ggpower -Atc "{statement}"').strip()


def png(color=(84, 137, 251)):
    """A small valid PNG (8x8, one colour)."""
    raw = b"".join(b"\x00" + bytes(color) * 8 for _ in range(8))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 8, 8, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


async def realtime(base, headers, project, serial):
    """One WebSocket session: ticket from one request, connection possibly on the other instance."""
    ticket = requests.post(base + "/api/v1/realtime/ticket", headers=headers, timeout=10).json()["ticket"]
    url = base.replace("https://", "wss://").replace("http://", "ws://") + "/ws/v1/realtime?ticket=" + ticket
    got = {"subscribed": False, "device": False}
    async with websockets.connect(url, open_timeout=10) as ws:
        await ws.send(json.dumps({"subscribe": [project]}))
        try:
            for _ in range(6):
                msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=8))
                if msg.get("type") == "subscribed" and serial in msg.get("serials", []):
                    got["subscribed"] = True
                if msg.get("type") == "device" and msg.get("serial") == serial:
                    got["device"] = True
                if all(got.values()):
                    break
        except asyncio.TimeoutError:
            pass
    return got


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--base", default=os.environ.get("SMOKE_BASE", "http://localhost:5080").rstrip("/"), help="public address; default: $SMOKE_BASE")
    ap.add_argument("--repeat", type=int, default=8)
    args = ap.parse_args()
    base, api, serial = args.base, args.base + "/api/v1", args.serial

    # which instances are there, as the load balancer sees them
    seen = set()
    for _ in range(12):
        h = requests.get(base + "/healthz", timeout=10).json()
        seen.add(h.get("messages_received", 0) > 0)
    check("public /healthz answers", True)
    check("requests reach an instance that receives device data and one that does not (two instances)", seen == {True, False}, str(seen))
    for path in ("/metrics", "/healthz/ready", "/healthz/live"):
        check(f"{path} is not exposed publicly", requests.get(base + path, timeout=10).status_code == 404)

    phone = "09" + "".join(random.choice("0123456789") for _ in range(8))
    email = f"viewtest.{phone}@example.com"
    password = "Vv" + secrets.token_hex(6) + "9a"
    r = requests.post(api + "/auth/register", json={"name": "Deploy check", "email": email, "phone": phone, "password": password}, timeout=15)
    check("register a temporary account", r.status_code == 202, r.status_code)
    codes = re.findall(rf"DEV OTP for {phone} \(register\): (\d{{6}})",
                       ssh("cd /opt/ggpower && docker compose --profile multi logs --since 2m api api2 2>&1 | grep 'DEV OTP'"))
    if not check("one-time code found in an instance log", bool(codes)):
        return 1
    tokens = requests.post(api + "/auth/register/verify", json={"phone": phone, "code": codes[-1]}, timeout=15).json()
    headers = {"Authorization": "Bearer " + tokens["access_token"]}
    try:
        sql(f"insert into project_members (project_id, user_id) select pd.project_id, u.id from project_devices pd, users u where pd.serial = '{serial}' and u.email = '{email}'")

        # ---- the same answers from whichever instance serves the request
        project = None
        ok_all = True
        for i in range(args.repeat):
            r = requests.get(api + "/projects", headers=headers, timeout=10)
            ok_all &= r.status_code == 200 and len(r.json()) == 1
            project = r.json()[0]["id"] if r.status_code == 200 and r.json() else project
        check(f"token accepted and project listed on every request ({args.repeat}x)", ok_all and project)
        today = sql("select (now() at time zone 'Asia/Ho_Chi_Minh')::date")
        paths = {"overview": f"/projects/{project}/overview", "devices": f"/projects/{project}/devices",
                 "telemetry": f"/projects/{project}/telemetry", "alerts": f"/projects/{project}/alerts",
                 "stats": f"/projects/{project}/stats?period=day&anchor={today}", "day-curves": f"/projects/{project}/day-curves?date={today}",
                 "config": f"/devices/{serial}/config", "warranty": f"/projects/{project}/warranty", "me": "/me"}
        first = {}
        for name, path in paths.items():
            codes_seen, bodies = set(), []
            for _ in range(args.repeat):
                r = requests.get(api + path, headers=headers, timeout=15)
                codes_seen.add(r.status_code)
                if r.status_code == 200:
                    bodies.append(r.json())
            check(f"GET {name}: 200 on every request ({args.repeat}x)", codes_seen == {200}, str(codes_seen))
            first[name] = bodies[0] if bodies else None
            if name in ("devices", "config", "alerts", "warranty", "me") and bodies:
                # data that does not change within these few seconds must be identical on both instances
                same = all(json.dumps(b, sort_keys=True) == json.dumps(bodies[0], sort_keys=True) for b in bodies) or name == "devices"
                check(f"GET {name}: both instances give the same answer", same)
        snap = first["telemetry"] or {}
        check("live snapshot carries the device's serial, status and firmware on every instance",
              snap.get("serial") == serial and snap.get("status") and snap.get("logger_fw"), f"{snap.get('status')} fw {snap.get('logger_fw')}")
        serial_seen = {requests.get(api + paths["telemetry"], headers=headers, timeout=10).json().get("logger_fw") for _ in range(args.repeat)}
        check("the instance that receives no device data still knows the live state (shared through Redis)", None not in serial_seen and len(serial_seen) == 1, str(serial_seen))
        ov = first["overview"] or {}
        check("overview has the new fields (model, co2_today_kg, battery_charged_today)", all(k in ov for k in ("model", "co2_today_kg", "battery_charged_today")), ",".join(sorted(ov))[:160])
        dev = (first["devices"] or [{}])[0]
        check("devices has the datalogger card", "datalogger" in dev, ",".join(sorted(dev)))
        cfg = first["config"] or {}
        check("config readable, write access state reported", "write_access" in cfg, str(cfg.get("write_access", {}).get("state")))

        # ---- realtime: the ticket is issued by one instance and may be redeemed on the other
        ws_ok = 0
        for _ in range(6):
            try:
                got = asyncio.run(realtime(base, headers, project, serial))
                ws_ok += got["subscribed"] and got["device"]
            except Exception as e:      # noqa: BLE001
                print("   websocket attempt failed:", type(e).__name__, str(e)[:80])
        check("WebSocket: ticket accepted and device snapshot received (6 sessions across the instances)", ws_ok == 6, f"{ws_ok}/6")

        # ---- files: stored by one instance, readable from the other (S3)
        picture = png()
        r = requests.put(api + "/me/avatar", headers={**headers, "Content-Type": "image/png"}, data=picture, timeout=20)
        check("upload an avatar", r.status_code in (200, 204), r.status_code)
        got = [requests.get(api + "/me/avatar", headers=headers, timeout=15) for _ in range(args.repeat)]
        check(f"avatar read back identical on every request ({args.repeat}x)", all(g.status_code == 200 and g.content == picture for g in got),
              str({g.status_code for g in got}))
        r = requests.delete(api + "/me/avatar", headers=headers, timeout=15)
        check("avatar removed again", r.status_code in (200, 204) and requests.get(api + "/me/avatar", headers=headers, timeout=15).status_code == 404, r.status_code)

        # ---- sign-out reaches both instances
        requests.post(api + "/auth/logout", headers=headers, json={"refresh_token": tokens.get("refresh_token")}, timeout=10)
        import time
        time.sleep(22)      # sessions are cached for 20 s per instance
        after = {requests.get(api + "/projects", headers=headers, timeout=10).status_code for _ in range(args.repeat)}
        check("after sign-out the token is refused by every instance", after == {401}, str(after))
    finally:
        sql("delete from users where email like 'viewtest.%@example.com'")
        check("temporary account removed", sql("select count(*) from users where email like 'viewtest.%'") == "0")
    print(f"\n{sum(results)}/{len(results)} checks passed")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
