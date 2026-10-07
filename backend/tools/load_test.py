"""Load test: N simulated devices publish telemetry every 5 s through the real broker.

    python tools/load_test.py [devices=200] [seconds=180]

Uses tenant "dev" and serials LOAD-0001..; measures how long a message takes from publish to the
app's realtime channel, and the backend's batch-write time. Removes its devices afterwards.
"""
import asyncio, json, os, ssl, statistics, sys, threading, time
import requests, websockets
import paho.mqtt.client as mqtt
from smoke_test import API, BASE, H, new_account
from command_test import SECRETS, sql

N = int(sys.argv[1]) if len(sys.argv) > 1 else 200
SECONDS = int(sys.argv[2]) if len(sys.argv) > 2 else 180
WATCH = "LOAD-0001"


def payload(sn, i):
    pv = 1500 + (i * 37) % 2000
    return json.dumps({"sn": sn, "ts": int(time.time()), "sent_ms": int(time.time() * 1000), "inv_status": 3, "inv_temp": 40.5,
        "pv": {"v_pv1": 320.1, "i_pv1": 4.2, "p_pv1": pv / 2, "v_pv2": 318.4, "i_pv2": 4.1, "p_pv2": pv / 2, "p_total": pv},
        "grid": {"v_ac": 229.8, "i_ac": 1.2, "freq": 50.01, "p_active": 250, "p_direction": "import"},
        "load": {"v_out": 230.0, "i_out": 5.1, "p_active": 1100, "freq": 50.0},
        "battery": {"p_flow": -(pv - 850), "p_direction": "charging", "soc": 60 + i % 30},
        "energy": {"yield_today_kwh": 10.2, "yield_total_kwh": 1500.5, "load_today_kwh": 8.1, "grid_export_today_kwh": 0.0}}, separators=(",", ":"))


def main():
    serials = [f"LOAD-{i:04d}" for i in range(1, N + 1)]
    m = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="loadtest-" + str(os.getpid()))
    m.username_pw_set(SECRETS["MQTT_USER"], SECRETS["MQTT_PASSWORD"])
    m.tls_set(cert_reqs=ssl.CERT_REQUIRED)
    m.connect(SECRETS["MQTT_HOST"], int(SECRETS.get("MQTT_PORT", 8883)), 30)
    m.loop_start()
    time.sleep(2)
    for sn in serials[:1]:
        m.publish(f"solar/dev/inverter/{sn}/up/status", json.dumps({"sn": sn, "state": "online", "ts": int(time.time()), "model": "Load test", "inverter_comm": "ok"}), qos=1)
    time.sleep(2)

    _, _, tok = new_account("load")
    r = requests.post(API + "/projects", headers=H(tok), json={"serial": WATCH, "name": "Load", "site_type": "home", "address": "x", "installed_on": "2026-01-01", "pv_kwp": 5})
    assert r.status_code == 201, r.text
    pid = r.json()["project_id"]

    stop = threading.Event()
    sent = [0]

    def publisher():
        i = 0
        while not stop.is_set():
            t0 = time.time()
            for k, sn in enumerate(serials):
                m.publish(f"solar/dev/inverter/{sn}/up/telemetry", payload(sn, i + k), qos=0)
                sent[0] += 1
                time.sleep(5.0 / N * 0.9)      # spread the devices over the 5 s period
            i += 1
            time.sleep(max(0, 5 - (time.time() - t0)))

    lags, api_ms, health = [], [], []

    async def watch():
        ticket = requests.post(API + "/realtime/ticket", headers=H(tok)).json()["ticket"]
        async with websockets.connect(BASE.replace("http", "ws", 1) + "/ws/v1/realtime?ticket=" + ticket) as ws:
            await ws.send(json.dumps({"subscribe": [pid]}))
            end = time.time() + SECONDS
            last_probe = 0
            while time.time() < end:
                try:
                    msg = json.loads(await asyncio.wait_for(ws.recv(), 6))
                except asyncio.TimeoutError:
                    continue
                if msg.get("type") == "device" and msg.get("telemetry"):
                    # device timestamps have 1 s resolution; the publisher's own clock gives the true lag
                    lags.append(time.time() - last_sent.get("t", time.time()))
                if time.time() - last_probe > 10:
                    last_probe = time.time()
                    t0 = time.perf_counter()
                    requests.get(f"{API}/projects/{pid}/overview", headers=H(tok))
                    api_ms.append((time.perf_counter() - t0) * 1000)
                    health.append(requests.get(BASE + "/healthz").json())

    # record when the watched device's message leaves, to measure publish -> app
    last_sent = {}
    orig = m.publish
    def tracked(topic, *a, **k):
        if topic.endswith(f"/{WATCH}/up/telemetry"): last_sent["t"] = time.time()
        return orig(topic, *a, **k)
    m.publish = tracked

    h0 = requests.get(BASE + "/healthz").json()
    th = threading.Thread(target=publisher, daemon=True); th.start()
    t_start = time.time()
    asyncio.run(watch())
    stop.set(); th.join(8)
    time.sleep(3)
    h1 = requests.get(BASE + "/healthz").json()
    elapsed = time.time() - t_start

    written = h1["rows_written"] - h0["rows_written"]
    batches = [h["last_batch_ms"] for h in health]
    print(f"devices: {N}, duration: {elapsed:.0f} s, published: {sent[0]} ({sent[0]/elapsed:.0f} msg/s)")
    print(f"rows written by backend during the run (all devices on the broker): {written}")
    if lags:
        q = statistics.quantiles(lags, n=20)
        print(f"publish -> app realtime: median {statistics.median(lags)*1000:.0f} ms, p95 {q[18]*1000:.0f} ms, max {max(lags)*1000:.0f} ms ({len(lags)} updates)")
    if api_ms: print(f"GET overview under load: median {statistics.median(api_ms):.0f} ms, max {max(api_ms):.0f} ms")
    if batches: print(f"batch write: median {statistics.median(batches):.1f} ms, max {max(batches):.1f} ms")
    stored = sql("select count(*) from telemetry_raw where serial like 'LOAD-%'")
    print(f"rows stored for the load devices: {stored} of {sent[0]} published ({100*int(stored)/max(sent[0],1):.1f} %)")

    m.loop_stop()
    sql("delete from users where email like 'smoke.%@example.com'")
    sql("delete from telemetry_raw where serial like 'LOAD-%'"); sql("delete from rollup_15m where serial like 'LOAD-%'"); sql("delete from rollup_1d where serial like 'LOAD-%'")
    print("note: LOAD-* device rows stay in the cache until the backend restarts; run cleanup after a restart:")
    print("      delete from devices where serial like 'LOAD-%'")


if __name__ == "__main__":
    main()
