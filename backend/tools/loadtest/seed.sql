-- Steady-state history for N load-test devices (LOAD-00001…), so the roll-up job works on as much data as in production:
-- 4 days of 15-minute roll-ups and the last 35 minutes of 5-second raw samples.
--   docker exec -i ggpower-pg-test psql -U ggpower -d ggpower_load -v n=2000 < seed.sql
-- Run after the backend started once (it creates the tables and today's partitions).

DELETE FROM rollup_15m WHERE serial LIKE 'LOAD-%';
DELETE FROM rollup_1d WHERE serial LIKE 'LOAD-%';
DELETE FROM telemetry_raw WHERE serial LIKE 'LOAD-%';

INSERT INTO rollup_15m (serial, bucket, samples, pv_w, grid_w, load_w, batt_w, soc,
                        pv_kwh, load_kwh, import_kwh, export_kwh, charge_kwh, discharge_kwh, pv_self_kwh, pv_charge_kwh, pv_export_kwh)
SELECT 'LOAD-' || lpad(i::text, 5, '0'), b, 180, 3000, 200, 1200, 1600, 60,
       0.75, 0.3, 0.05, 0.0, 0.4, 0.0, 0.3, 0.4, 0.05
FROM generate_series(1, :n) AS i,
     generate_series(date_bin('15 minutes', now() - interval '4 days', TIMESTAMPTZ '2000-01-01'),
                     date_bin('15 minutes', now() - interval '40 minutes', TIMESTAMPTZ '2000-01-01'), interval '15 minutes') AS b;

INSERT INTO telemetry_raw (serial, ts, inv_status, pv_w, grid_w, load_w, batt_w, soc)
SELECT 'LOAD-' || lpad(i::text, 5, '0'), t, 3, 3000, 200, 1200, 1600, 60
FROM generate_series(1, :n) AS i,
     generate_series(now() - interval '35 minutes', now() - interval '10 seconds', interval '5 seconds') AS t;

ANALYZE rollup_15m;
ANALYZE telemetry_raw;
SELECT (SELECT count(*) FROM rollup_15m WHERE serial LIKE 'LOAD-%') AS rollup_rows,
       (SELECT count(*) FROM telemetry_raw WHERE serial LIKE 'LOAD-%') AS raw_rows;
