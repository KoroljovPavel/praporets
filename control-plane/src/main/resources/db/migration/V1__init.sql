CREATE TABLE environment
(
    id         UUID PRIMARY KEY,
    key        VARCHAR(64)  NOT NULL UNIQUE,    -- dev, staging, prod
    name       VARCHAR(128) NOT NULL,
    revision   BIGINT       NOT NULL DEFAULT 0, -- монотонний лічильник
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE flag
(
    id          UUID PRIMARY KEY,
    key         VARCHAR(128) NOT NULL UNIQUE,
    name        VARCHAR(256) NOT NULL,
    description TEXT,
    value_type  VARCHAR(16)  NOT NULL,          -- BOOLEAN|STRING|NUMBER|JSON
    archived    BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0 -- @Version, оптимістичний лок
);

CREATE TABLE variant
(
    id      UUID PRIMARY KEY,
    flag_id UUID        NOT NULL REFERENCES flag (id) ON DELETE CASCADE,
    key     VARCHAR(64) NOT NULL, -- on, off, control, treatment-a
    value   JSONB       NOT NULL,
    UNIQUE (flag_id, key)
);

CREATE TABLE segment
(
    id             UUID PRIMARY KEY,
    environment_id UUID         NOT NULL REFERENCES environment (id),
    key            VARCHAR(128) NOT NULL,
    conditions     JSONB        NOT NULL, -- [{attribute, operator, values}]
    version        BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (environment_id, key)
);

CREATE TABLE flag_config
(
    id              UUID PRIMARY KEY,
    flag_id         UUID        NOT NULL REFERENCES flag (id) ON DELETE CASCADE,
    environment_id  UUID        NOT NULL REFERENCES environment (id),
    enabled         BOOLEAN     NOT NULL DEFAULT false,
    default_variant VARCHAR(64) NOT NULL,
    off_variant     VARCHAR(64) NOT NULL,              -- значення коли enabled = false
    rules           JSONB       NOT NULL DEFAULT '[]', -- впорядкований список правил
    rollout         JSONB,                             -- {salt, buckets:[{variant, weight}]}
    version         BIGINT      NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (flag_id, environment_id)
);

CREATE TABLE revision_log
(
    id             BIGSERIAL PRIMARY KEY,
    environment_id UUID        NOT NULL REFERENCES environment (id),
    revision       BIGINT      NOT NULL,
    change_type    VARCHAR(32) NOT NULL, -- FLAG_UPDATED|SEGMENT_UPDATED|...
    payload        JSONB       NOT NULL, -- дельта цієї ревізії
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (environment_id, revision)
);

CREATE TABLE audit_log
(
    id           BIGSERIAL PRIMARY KEY,
    actor        VARCHAR(128) NOT NULL,
    action       VARCHAR(64)  NOT NULL,
    entity_type  VARCHAR(64)  NOT NULL,
    entity_id    UUID         NOT NULL,
    before_state JSONB,
    after_state  JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
