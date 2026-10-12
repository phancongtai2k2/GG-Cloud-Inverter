-- Plan B2: at most one open alert per device and fault, enforced by the database so that two instances raising the
-- same fault at once cannot create two. Duplicates left by the old read-then-write code are merged first: the oldest
-- open alert stays open and takes the others' occurrences; the others are closed (kept as history).
WITH ranked AS (
    SELECT id, serial, code, occurrences,
           row_number() OVER (PARTITION BY serial, code ORDER BY started_at, id) AS n,
           first_value(id) OVER (PARTITION BY serial, code ORDER BY started_at, id) AS keep
    FROM alerts WHERE ended_at IS NULL
), extra AS (
    SELECT keep, sum(occurrences) AS occ FROM ranked WHERE n > 1 GROUP BY keep
), merged AS (
    UPDATE alerts a SET occurrences = a.occurrences + e.occ FROM extra e WHERE a.id = e.keep
)
UPDATE alerts a SET ended_at = GREATEST(a.started_at, COALESCE(a.last_triggered_at, a.started_at))
FROM ranked r WHERE a.id = r.id AND r.n > 1;

DROP INDEX alerts_open_ix;
CREATE UNIQUE INDEX alerts_open_uq ON alerts (serial, code) WHERE ended_at IS NULL;
