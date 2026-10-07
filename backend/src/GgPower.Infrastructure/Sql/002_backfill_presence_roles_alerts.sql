-- Samples stored by the datalogger while its uplink was down stand for a longer interval than live ones.
ALTER TABLE telemetry_raw ADD COLUMN span_s smallint;

-- Whether a battery is connected is what the inverter reports, never something the backend derives.
ALTER TABLE telemetry_raw ADD COLUMN batt_present boolean;
ALTER TABLE battery_raw ADD COLUMN present boolean;
ALTER TABLE battery_raw ADD COLUMN bms_comm text;

-- A fault that comes back shortly after it cleared is the same alert, counted, not a new one.
ALTER TABLE alerts ADD COLUMN occurrences int NOT NULL DEFAULT 1;
ALTER TABLE alerts ADD COLUMN last_triggered_at timestamptz;
CREATE INDEX alerts_code_ix ON alerts (serial, code, started_at DESC);

-- The role of a user lives in one place: users.role.
ALTER TABLE project_members DROP COLUMN role;
