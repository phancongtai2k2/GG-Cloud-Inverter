-- GG Power backend schema. Applied once by Migrator (see schema_migrations).

CREATE TABLE users (
    id                  uuid PRIMARY KEY,
    name                text        NOT NULL,
    email               text        NOT NULL,           -- stored lower-case
    phone               text        NOT NULL,           -- VN local form 0xxxxxxxxx
    password_hash       text        NOT NULL,
    role                text        NOT NULL DEFAULT 'owner',   -- owner | distributor | ggp_technician
    write_admin         boolean     NOT NULL DEFAULT false,     -- may use /admin/config-write-*
    status              text        NOT NULL DEFAULT 'pending', -- pending | active | deletion_requested
    language            text,
    avatar_file         text,
    notif_device_alerts boolean     NOT NULL DEFAULT true,
    notif_daily_report  boolean     NOT NULL DEFAULT true,
    notif_news          boolean     NOT NULL DEFAULT false,
    failed_logins       int         NOT NULL DEFAULT 0,
    locked_until        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX users_email_uq ON users (email);
CREATE UNIQUE INDEX users_phone_uq ON users (phone);

-- Refresh-token sessions. Only a SHA-256 of the token is stored. A family groups the rotations of
-- one login; presenting an already rotated token revokes the whole family.
CREATE TABLE sessions (
    id           uuid PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES users ON DELETE CASCADE,
    family_id    uuid        NOT NULL,
    refresh_hash bytea       NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    expires_at   timestamptz NOT NULL,
    rotated_at   timestamptz,
    revoked_at   timestamptz,
    user_agent   text,
    ip           text
);
CREATE UNIQUE INDEX sessions_hash_uq ON sessions (refresh_hash);
CREATE INDEX sessions_family_ix ON sessions (family_id);
CREATE INDEX sessions_user_ix ON sessions (user_id);

CREATE TABLE otp_codes (
    id          uuid PRIMARY KEY,
    purpose     text        NOT NULL,   -- register | reset
    destination text        NOT NULL,   -- phone or email the code was sent to
    user_id     uuid        REFERENCES users ON DELETE CASCADE,
    code_hash   bytea       NOT NULL,
    attempts    int         NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz
);
CREATE INDEX otp_dest_ix ON otp_codes (purpose, destination, created_at DESC);

CREATE TABLE reset_tokens (
    token_hash bytea PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users ON DELETE CASCADE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz
);

CREATE TABLE push_tokens (
    token      text PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users ON DELETE CASCADE,
    platform   text        NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- One row per datalogger/inverter ever seen on MQTT (keyed by the inverter serial).
CREATE TABLE devices (
    serial               text PRIMARY KEY,
    tenant               text        NOT NULL,
    model                text,
    inverter_fw          text,
    logger_fw            text,
    online               boolean     NOT NULL DEFAULT false,
    inverter_comm        text,
    first_seen           timestamptz NOT NULL DEFAULT now(),
    last_seen            timestamptz NOT NULL DEFAULT now(),
    status_json          jsonb,
    config_state         jsonb,
    config_state_at      timestamptz,
    firmware_json        jsonb,
    rated_power_kw       double precision,
    battery_capacity_kwh double precision,
    batt_from_pv_kwh     double precision NOT NULL DEFAULT 0,   -- estimated origin of the stored energy
    batt_from_grid_kwh   double precision NOT NULL DEFAULT 0
);

CREATE TABLE projects (
    id            uuid PRIMARY KEY,
    owner_id      uuid        NOT NULL REFERENCES users ON DELETE CASCADE,
    name          text        NOT NULL,
    site_type     text        NOT NULL DEFAULT 'home',   -- home | factory
    address       text        NOT NULL DEFAULT '',
    latitude      double precision,
    longitude     double precision,
    time_zone     text        NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    installed_on  date        NOT NULL,
    pv_kwp        double precision NOT NULL,
    photo_file    text,
    tariff_kind   text        NOT NULL DEFAULT 'evn_tiered',  -- evn_tiered | fixed
    tariff_fixed  bigint,
    tariff_sell   bigint      NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX projects_owner_ix ON projects (owner_id);

CREATE TABLE project_members (
    project_id uuid NOT NULL REFERENCES projects ON DELETE CASCADE,
    user_id    uuid NOT NULL REFERENCES users ON DELETE CASCADE,
    role       text NOT NULL,    -- owner | distributor | ggp_technician
    PRIMARY KEY (project_id, user_id)
);
CREATE INDEX project_members_user_ix ON project_members (user_id);

-- A device belongs to at most one project.
CREATE TABLE project_devices (
    serial     text PRIMARY KEY REFERENCES devices,
    project_id uuid NOT NULL REFERENCES projects ON DELETE CASCADE,
    name       text NOT NULL,
    added_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX project_devices_project_ix ON project_devices (project_id);

-- Raw samples (5 s telemetry, 20 s battery), partitioned by month; old partitions are dropped.
CREATE TABLE telemetry_raw (
    serial         text        NOT NULL,
    ts             timestamptz NOT NULL,
    inv_status     smallint,
    inv_temp       real,
    pv1_v real, pv1_a real, pv1_w real,
    pv2_v real, pv2_a real, pv2_w real,
    pv_w           real,
    grid_v real, grid_a real, grid_hz real,
    grid_w         real,      -- + import, - export
    load_v real, load_a real, load_hz real,
    load_w         real,
    batt_w         real,      -- + charging, - discharging (app convention; firmware sign is flipped at ingest)
    soc            real,
    e_yield_today real, e_yield_total real, e_load_today real, e_export_today real, e_import_today real,
    PRIMARY KEY (serial, ts)
) PARTITION BY RANGE (ts);

CREATE TABLE battery_raw (
    serial     text        NOT NULL,
    ts         timestamptz NOT NULL,
    soc real, soh real, voltage real,
    current_a  real,       -- + charging, - discharging
    power_w real, temp_cells real, temp_bms real,
    cycles int, cap_remaining_ah real, cap_nominal_ah real, est_minutes real,
    PRIMARY KEY (serial, ts)
) PARTITION BY RANGE (ts);

-- 15-minute aggregates: averages for charts, integrated energy for statistics.
CREATE TABLE rollup_15m (
    serial   text        NOT NULL,
    bucket   timestamptz NOT NULL,
    samples  int         NOT NULL,
    pv_w real, grid_w real, load_w real, batt_w real, soc real,
    pv1_v real, pv1_a real, pv1_w real, pv2_v real, pv2_a real, pv2_w real,
    grid_v real, grid_a real, grid_hz real, load_v real, load_a real, inv_temp real,
    batt_v real, batt_a real, batt_temp real,
    pv_kwh double precision NOT NULL DEFAULT 0,
    load_kwh double precision NOT NULL DEFAULT 0,
    import_kwh double precision NOT NULL DEFAULT 0,
    export_kwh double precision NOT NULL DEFAULT 0,
    charge_kwh double precision NOT NULL DEFAULT 0,
    discharge_kwh double precision NOT NULL DEFAULT 0,
    pv_self_kwh double precision NOT NULL DEFAULT 0,
    pv_charge_kwh double precision NOT NULL DEFAULT 0,
    pv_export_kwh double precision NOT NULL DEFAULT 0,
    PRIMARY KEY (serial, bucket)
);

-- Daily totals in the device's local day (for month / year statistics).
CREATE TABLE rollup_1d (
    serial   text NOT NULL,
    day      date NOT NULL,
    pv_kwh double precision NOT NULL DEFAULT 0,
    load_kwh double precision NOT NULL DEFAULT 0,
    import_kwh double precision NOT NULL DEFAULT 0,
    export_kwh double precision NOT NULL DEFAULT 0,
    charge_kwh double precision NOT NULL DEFAULT 0,
    discharge_kwh double precision NOT NULL DEFAULT 0,
    pv_self_kwh double precision NOT NULL DEFAULT 0,
    pv_charge_kwh double precision NOT NULL DEFAULT 0,
    pv_export_kwh double precision NOT NULL DEFAULT 0,
    soc_avg real,
    PRIMARY KEY (serial, day)
);

CREATE TABLE alerts (
    id          uuid PRIMARY KEY,
    serial      text        NOT NULL REFERENCES devices,
    alarm_id    text        NOT NULL,         -- id from the device, or generated for backend alerts
    code        text        NOT NULL,
    severity    text        NOT NULL,         -- error | warning | info
    title       text        NOT NULL,
    description text        NOT NULL DEFAULT '',
    started_at  timestamptz NOT NULL,
    ended_at    timestamptz,
    resolved    boolean     NOT NULL DEFAULT false,
    resolved_by uuid        REFERENCES users ON DELETE SET NULL,
    snapshot    jsonb
);
CREATE UNIQUE INDEX alerts_alarm_uq ON alerts (serial, alarm_id);
CREATE INDEX alerts_serial_ix ON alerts (serial, started_at DESC);
CREATE INDEX alerts_open_ix ON alerts (serial, code) WHERE ended_at IS NULL;

CREATE TABLE config_commands (
    id               uuid PRIMARY KEY,
    serial           text        NOT NULL REFERENCES devices,
    user_id          uuid        REFERENCES users ON DELETE SET NULL,
    user_name        text,
    user_role        text,
    grp              text        NOT NULL,     -- operating_mode | backup_soc | tou_schedule
    status           text        NOT NULL,
    request          jsonb       NOT NULL,
    request_hash     bytea       NOT NULL,
    idempotency_key  uuid        NOT NULL,
    changes          jsonb,
    outcome          jsonb,
    failure          jsonb,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    expires_at       timestamptz NOT NULL
);
CREATE UNIQUE INDEX config_commands_idem_uq ON config_commands (user_id, idempotency_key);
CREATE INDEX config_commands_serial_ix ON config_commands (serial, created_at DESC);

-- TOU strategies are kept by the backend; the inverter only holds today's slots.
CREATE TABLE tou_strategies (
    serial     text        PRIMARY KEY REFERENCES devices,
    strategies jsonb       NOT NULL DEFAULT '[]',
    tou_active boolean     NOT NULL DEFAULT false,   -- the user chose the "tou" operating mode
    loaded_strategy_id text,                          -- strategy whose slots are on the inverter now
    loaded_on  date,                                  -- local day it was loaded for
    saved_grid_mode text,                             -- grid_mode before entering max_grid_export
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE config_allowlist (
    serial   text PRIMARY KEY,
    note     text,
    added_by uuid REFERENCES users ON DELETE SET NULL,
    added_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_settings (
    key        text PRIMARY KEY,
    value      jsonb       NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO app_settings (key, value) VALUES ('config_write_switch', '{"enabled": true}');

CREATE TABLE audit_log (
    id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    at      timestamptz NOT NULL DEFAULT now(),
    user_id uuid,
    action  text        NOT NULL,
    target  text,
    detail  jsonb,
    ip      text
);
CREATE INDEX audit_at_ix ON audit_log (at);

CREATE SEQUENCE ticket_seq START 151;
CREATE TABLE tickets (
    id          uuid PRIMARY KEY,
    code        text        NOT NULL,
    user_id     uuid        NOT NULL REFERENCES users ON DELETE CASCADE,
    project_id  uuid        REFERENCES projects ON DELETE SET NULL,
    alert_id    uuid        REFERENCES alerts ON DELETE SET NULL,
    type        text        NOT NULL,      -- technical | account | other
    title       text        NOT NULL,
    description text        NOT NULL,
    status      text        NOT NULL DEFAULT 'received',  -- received | processing | closed
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX tickets_user_ix ON tickets (user_id, created_at DESC);

CREATE TABLE ticket_messages (
    id         uuid PRIMARY KEY,
    ticket_id  uuid        NOT NULL REFERENCES tickets ON DELETE CASCADE,
    author     text        NOT NULL,       -- user | support
    body       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ticket_messages_ix ON ticket_messages (ticket_id, created_at);

CREATE TABLE ticket_attachments (
    id           uuid PRIMARY KEY,
    ticket_id    uuid        NOT NULL REFERENCES tickets ON DELETE CASCADE,
    content_type text        NOT NULL,
    size_bytes   bigint      NOT NULL,
    stored_name  text        NOT NULL,     -- server-generated file name
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE warranty_devices (
    id           uuid PRIMARY KEY,
    project_id   uuid NOT NULL REFERENCES projects ON DELETE CASCADE,
    name         text NOT NULL,
    serial       text NOT NULL,
    activated_on date NOT NULL,
    expires_on   date NOT NULL
);
CREATE INDEX warranty_devices_ix ON warranty_devices (project_id);

CREATE TABLE warranty_history (
    id         uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES projects ON DELETE CASCADE,
    title      text NOT NULL,
    device     text NOT NULL,
    work_kind  text NOT NULL,     -- repair | firmware | replace | inspect
    work_ref   text,              -- ticket code, or "old>new" firmware versions
    on_date    date NOT NULL,
    channel    text NOT NULL,     -- on_site | remote
    status     text NOT NULL DEFAULT 'completed'
);
CREATE INDEX warranty_history_ix ON warranty_history (project_id, on_date DESC);
