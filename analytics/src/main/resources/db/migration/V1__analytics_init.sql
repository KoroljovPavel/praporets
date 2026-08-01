CREATE TABLE evaluation_agg
(
    environment  VARCHAR(64)  NOT NULL,
    flag_key     VARCHAR(128) NOT NULL,
    variant_key  VARCHAR(64)  NOT NULL,
    window_start TIMESTAMPTZ  NOT NULL,
    eval_count   BIGINT       NOT NULL,
    unique_users BIGINT       NOT NULL,
    PRIMARY KEY (environment, flag_key, variant_key, window_start)
);

CREATE TABLE processed_event
(
    evaluation_id UUID PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
