-- Plan A4: incremental roll-up moves each day row by (new − old) of the 15-minute buckets it changes, so the average
-- SOC is kept as a sum and a count. Days outside the start-up full pass (last ~3 days) never change again and keep 0 / 0;
-- their soc_avg is untouched.
ALTER TABLE rollup_1d ADD COLUMN soc_sum double precision NOT NULL DEFAULT 0, ADD COLUMN soc_n int NOT NULL DEFAULT 0;

-- Both tables are updated in place every 30 s. Free space in each page lets PostgreSQL do that without moving the row
-- (HOT update): less bloat, and a device's rows stay together. Applies to pages written from now on.
ALTER TABLE rollup_15m SET (fillfactor = 70);
ALTER TABLE rollup_1d SET (fillfactor = 70);
