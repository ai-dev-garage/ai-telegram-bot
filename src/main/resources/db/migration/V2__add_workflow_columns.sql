-- Workflow orchestration: parent/child job relationship for multi-step workflows.
-- A WORKFLOW_TASK parent job decomposes into child jobs (SHELL_COMMAND, AGENT_TASK)
-- that execute sequentially with optional per-step approval gates.

ALTER TABLE jobs ADD COLUMN parent_job_id BIGINT REFERENCES jobs(id);
ALTER TABLE jobs ADD COLUMN step_index   INTEGER;
ALTER TABLE jobs ADD COLUMN step_id      VARCHAR(128);

CREATE INDEX idx_jobs_parent ON jobs(parent_job_id) WHERE parent_job_id IS NOT NULL;
