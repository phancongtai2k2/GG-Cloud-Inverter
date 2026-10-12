"""End-to-end smoke test against a running backend (python tools/smoke_test.py [base_url]).

Walks the main flows as the app would: register (one-time code read from the backend log in
development), sign in, token rotation, add a project for a device that is publishing, read live
and historical data, realtime channel, and the access checks between two accounts.
Only reads device data; it never sends a configuration command.
"""
import asyncio, json, os, random, re, sys, time
import requests, websockets

BASE = (sys.argv[1] if len(sys.argv) > 1 and sys.argv[1].startswith("http") else os.environ.get("SMOKE_BASE", "http://localhost:5080")).rstrip("/")
API = BASE + "/api/v1"
LOG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "backend.log")
SERIAL = os.environ.get("SMOKE_SERIAL")          # default: first unowned device the backend has seen
results = []


def check(name, ok, detail=""):
    ok = bool(ok)
    results.append(ok)
    print(("PASS " if ok else "FAIL ") + name + (f"  [{detail}]" if detail and not ok else ""))
    return ok


def _api_log():
    """Local run: backend.log. Remote run: the API container's log, read over SSH
    (SMOKE_SSH = "<host key fingerprint>;user@host", password in SMOKE_SSH_PW; nothing is stored)."""
    remote = os.environ.get("SMOKE_SSH")
    if not remote:
        with open(LOG, encoding="utf-8", errors="replace") as f:
            return f.read()
    import subprocess
    key, target = remote.split(";", 1)
    r = subprocess.run(["plink", "-batch", "-ssh", "-hostkey", key, "-pw", os.environ["SMOKE_SSH_PW"], target,
                        "cd /opt/ggpower && docker compose --profile multi logs --since 3m api api2 2>&1 | grep 'DEV OTP'"], capture_output=True, text=True)
    return r.stdout


def otp_for(destination):
    for _ in range(20):
        if True:
            hits = re.findall(rf"DEV OTP for {re.escape(destination)} \(\w+\): (\d{{6}})", _api_log())
        if hits:
            return hits[-1]
        time.sleep(0.3)
    raise RuntimeError("no OTP in log for " + destination)


def new_account(tag):
    phone = "09" + "".join(random.choice("0123456789") for _ in range(8))
    email = f"smoke.{tag}.{phone}@example.com"
    r = requests.post(API + "/auth/register", json={"name": f"Smoke {tag}", "email": email, "phone": phone, "password": "matkhau123"})
    assert r.status_code == 202, r.text
    r = requests.post(API + "/auth/register/verify", json={"phone": phone, "code": otp_for(phone)})
    assert r.status_code == 200, r.text
    return phone, email, r.json()


def H(tok):
    return {"Authorization": "Bearer " + tok["access_token"]}


def main():
    r = requests.get(BASE + "/healthz")
    check("health endpoint", r.status_code == 200 and r.json()["status"] == "ok", r.text)
    print("     ", r.text)

    # --- accounts ---
    phone, email, tok = new_account("a")
    check("register + verify signs in", "access_token" in tok and "refresh_token" in tok)
    check("no token -> 401", requests.get(API + "/me").status_code == 401)
    me = requests.get(API + "/me", headers=H(tok))
    check("profile", me.status_code == 200 and me.json()["phone"] == phone, me.text)
    etag = me.headers.get("ETag")
    check("conditional GET -> 304", requests.get(API + "/me", headers={**H(tok), "If-None-Match": etag}).status_code == 304)

    r = requests.post(API + "/auth/login", json={"account": email, "password": "wrong-pass1"})
    check("wrong password -> 401", r.status_code == 401 and r.json()["code"] == "INVALID_CREDENTIALS", r.text)
    r = requests.post(API + "/auth/login", json={"account": phone, "password": "matkhau123"})
    check("login by phone", r.status_code == 200, r.text)
    login = r.json()

    r = requests.post(API + "/auth/refresh", json={"refresh_token": login["refresh_token"]})
    check("refresh rotates", r.status_code == 200 and r.json()["refresh_token"] != login["refresh_token"], r.text)
    rotated = r.json()
    r = requests.post(API + "/auth/refresh", json={"refresh_token": login["refresh_token"]})
    check("reused refresh token rejected", r.status_code == 401, r.text)
    r = requests.post(API + "/auth/refresh", json={"refresh_token": rotated["refresh_token"]})
    check("...and the whole session is revoked", r.status_code == 401, r.text)

    r = requests.post(API + "/auth/register", json={"name": "x", "email": email, "phone": "0912345678", "password": "matkhau123"})
    check("duplicate e-mail -> 409", r.status_code == 409 and r.json()["code"] == "EMAIL_TAKEN", r.text)
    r = requests.post(API + "/auth/register", json={"name": "x", "email": "a@b.vn", "phone": "0987654321", "password": "short"})
    check("weak password -> 400", r.status_code == 400, r.text)

    # --- password reset ---
    r = requests.post(API + "/auth/password/forgot", json={"account": email})
    check("forgot password accepted", r.status_code == 202, r.text)
    r = requests.post(API + "/auth/password/verify", json={"account": email, "code": "000000"})
    check("wrong reset code", r.status_code == 400 and r.json().get("attempts_left") == 4, r.text)
    r = requests.post(API + "/auth/password/verify", json={"account": email, "code": otp_for(email)})
    check("right reset code", r.status_code == 200, r.text)
    reset_token = r.json()["reset_token"]
    r = requests.post(API + "/auth/password/reset", json={"reset_token": reset_token, "new_password": "matkhau456"})
    check("password reset", r.status_code == 204, r.text)
    check("reset token single use", requests.post(API + "/auth/password/reset", json={"reset_token": reset_token, "new_password": "matkhau789"}).status_code == 401)
    time.sleep(21)   # the session check is cached for 20 s
    check("old session signed out by reset", requests.get(API + "/me", headers=H(tok)).status_code == 401)
    tok = requests.post(API + "/auth/login", json={"account": email, "password": "matkhau456"}).json()
    check("login with new password", "access_token" in tok)

    # --- project + device ---
    r = requests.post(API + "/projects/check-serial", headers=H(tok), json={"serial": "NO-SUCH-DEVICE"})
    check("unknown serial -> 404", r.status_code == 404 and r.json()["code"] == "SERIAL_NOT_FOUND", r.text)
    serial = SERIAL
    if not serial:
        print("SKIP project flow: set SMOKE_SERIAL to an unowned device serial that is publishing")
        return finish()
    r = requests.post(API + "/projects/check-serial", headers=H(tok), json={"serial": serial})
    if r.status_code == 409:
        print("SKIP project flow: device already belongs to a project")
        return finish()
    check("known serial", r.status_code == 200, r.text)
    body = {"serial": serial, "name": "Nhà smoke test", "site_type": "home", "address": "Hưng Yên", "time_zone": "Asia/Ho_Chi_Minh",
            "installed_on": "2026-01-15", "pv_kwp": 5.5, "tariff": {"kind": "fixed", "fixed_per_kwh": 3000, "sell_per_kwh": 0}}
    r = requests.post(API + "/projects", headers=H(tok), json=body)
    check("create project", r.status_code == 201, r.text)
    pid = r.json()["project_id"]
    r = requests.post(API + "/projects", headers=H(tok), json=body)
    check("device cannot be added twice", r.status_code == 409 and r.json()["code"] == "SERIAL_OWNED", r.text)

    r = requests.get(API + "/projects", headers=H(tok))
    check("project list", r.status_code == 200 and len(r.json()) == 1, r.text)
    print("     ", json.dumps(r.json()[0], ensure_ascii=False))
    P = f"{API}/projects/{pid}"
    r = requests.get(P + "/overview", headers=H(tok))
    check("overview", r.status_code == 200 and r.json()["serial"] == serial, r.text)
    print("     ", r.text[:400])
    today = time.strftime("%Y-%m-%d")
    for path in (f"/day-curves?date={today}", f"/stats?period=day&anchor={today}", f"/stats?period=month&anchor={today}",
                 f"/stats?period=year&anchor={today}", "/devices", "/telemetry", "/history?range=hour&fields=pv_power,grid_voltage",
                 "/history?range=day&fields=soc", "/alerts", "/warranty"):
        r = requests.get(P + path, headers=H(tok))
        check("GET " + path.split("?")[0] + ("?" + path.split("?")[1][:22] if "?" in path else ""), r.status_code == 200, r.text[:200])
    r = requests.get(P + "/stats?period=day&anchor=2026-01-01", headers=H(tok))
    check("closed period is immutable", "immutable" in r.headers.get("Cache-Control", ""), r.headers.get("Cache-Control"))
    r = requests.get(f"{API}/devices/{serial}/config", headers=H(tok))
    check("device config", r.status_code == 200 and r.json()["write_access"]["state"] in ("device_read_only", "gateway_offline", "enabled"), r.text[:300])
    print("     ", r.text[:500])
    check("device not on the write list is read-only", r.json()["write_access"]["state"] == "device_read_only" and r.json()["write_access"]["writable_groups"] == [])

    # --- another account must not see any of it ---
    _, _, other = new_account("b")
    for path in ("/overview", "/devices", "/telemetry", "/alerts", "/warranty", f"/stats?period=day&anchor={today}"):
        check("other account GET " + path.split("?")[0] + " -> 404", requests.get(P + path, headers=H(other)).status_code == 404)
    check("other account device config -> 404", requests.get(f"{API}/devices/{serial}/config", headers=H(other)).status_code == 404)
    r = requests.post(f"{API}/devices/{serial}/config-commands", headers={**H(other), "Idempotency-Key": "6f1c1d52-6a0e-4a65-9d57-0c1c1a1d0001"},
                      json={"group": "operating_mode", "based_on_version": "x", "mode": "self_consumption"})
    check("other account cannot send commands -> 404", r.status_code == 404, r.text)
    check("admin endpoints need the write-admin flag", requests.get(API + "/admin/config-write-switch", headers=H(tok)).status_code == 403)

    # --- realtime ---
    async def realtime():
        ws_url = BASE.replace("http", "ws", 1) + "/ws/v1/realtime?ticket="
        try:
            async with websockets.connect(ws_url + "bogus"):
                check("bad ticket rejected", False)
        except Exception:
            check("bad ticket rejected", True)

        ticket = requests.post(API + "/realtime/ticket", headers=H(other)).json()["ticket"]
        async with websockets.connect(ws_url + ticket) as ws:
            await ws.send(json.dumps({"subscribe": [pid]}))
            first = json.loads(await asyncio.wait_for(ws.recv(), 5))
            check("other account gets no device on the realtime channel", first["type"] == "subscribed" and first["serials"] == [], str(first))

        ticket = requests.post(API + "/realtime/ticket", headers=H(tok)).json()["ticket"]
        async with websockets.connect(ws_url + ticket) as ws:
            t0 = time.time()
            await ws.send(json.dumps({"subscribe": [pid]}))
            first = json.loads(await asyncio.wait_for(ws.recv(), 5))
            check("subscribed", first["type"] == "subscribed" and first["serials"] == [serial], str(first))
            snap = json.loads(await asyncio.wait_for(ws.recv(), 5))
            check("snapshot right after subscribing", snap["type"] == "device" and time.time() - t0 < 1.0, f"{time.time()-t0:.2f}s")
            live = 0
            deadline = time.time() + 14
            while time.time() < deadline and live < 2:
                try:
                    m = json.loads(await asyncio.wait_for(ws.recv(), deadline - time.time()))
                except asyncio.TimeoutError:
                    break
                if m["type"] == "device" and m.get("telemetry"):
                    live += 1
                    lag = time.time() - time.mktime(time.strptime(m["telemetry"]["ts"][:19], "%Y-%m-%dT%H:%M:%S")) + time.timezone
                    print(f"      live update: pv={m['telemetry']['pv_w']} W grid={m['telemetry']['grid_w']} W, device timestamp {lag:.1f} s ago")
            check("live updates pushed", live >= 1, f"{live} updates in 14 s (device offline?)")
        try:
            async with websockets.connect(ws_url + ticket):
                check("ticket is single use", False)
        except Exception:
            check("ticket is single use", True)

    asyncio.run(realtime())
    return finish()


def finish():
    print(f"\n{sum(results)}/{len(results)} checks passed")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
