-- Serials are matched case-insensitively (upper(serial) = upper(@serial)), e.g. on every device access check.
-- Without an index on the same expression each lookup reads the whole table. (Plan A1, KE_HOACH_NANG_CAP_BACKEND.docx)
CREATE INDEX IF NOT EXISTS devices_serial_upper_ix ON devices (upper(serial));
CREATE INDEX IF NOT EXISTS project_devices_serial_upper_ix ON project_devices (upper(serial));
CREATE INDEX IF NOT EXISTS config_allowlist_serial_upper_ix ON config_allowlist (upper(serial));
CREATE INDEX IF NOT EXISTS config_commands_serial_upper_ix ON config_commands (upper(serial), created_at DESC);
