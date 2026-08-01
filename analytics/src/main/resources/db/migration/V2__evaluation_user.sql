CREATE TABLE evaluation_user
(
    environment   VARCHAR(64)  NOT NULL,
    flag_key      VARCHAR(128) NOT NULL,
    variant_key   VARCHAR(64)  NOT NULL,
    window_start  TIMESTAMPTZ  NOT NULL,
    user_key_hash VARCHAR(64)  NOT NULL,
    PRIMARY KEY (environment, flag_key, variant_key, window_start, user_key_hash)
);
