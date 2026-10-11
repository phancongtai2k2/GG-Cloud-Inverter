-- Plan B1: the daily report is sent once per project and day even when the job moves to another instance
-- (or the only one restarts) during the hour it is due.
CREATE TABLE daily_reports_sent (
    project_id uuid NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    day        date NOT NULL,
    sent_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, day)
);

-- The job leader sweeps commands abandoned by a stopped instance every 20 s; only unfinished ones are indexed.
CREATE INDEX config_commands_open_ix ON config_commands (updated_at) WHERE status NOT IN ('applied', 'reverted', 'failed');
