ALTER TABLE evaluation_agg
    ADD COLUMN rollout_count BIGINT NOT NULL DEFAULT 0;
