CREATE TABLE posting (
    id                    BIGSERIAL PRIMARY KEY,
    source                VARCHAR(100)  NOT NULL,
    external_id           VARCHAR(200)  NOT NULL,
    company               VARCHAR(200)  NOT NULL,
    title                 VARCHAR(500)  NOT NULL,
    location              VARCHAR(500)  NOT NULL,
    url                   VARCHAR(2000) NOT NULL,
    description           TEXT          NOT NULL,
    category              VARCHAR(20)   NOT NULL,
    coop_requirement      VARCHAR(20)   NOT NULL,
    must_return_to_school BOOLEAN       NOT NULL,
    term_months           INTEGER[]     NOT NULL,
    skills                TEXT[]        NOT NULL,
    first_seen_at         TIMESTAMPTZ   NOT NULL,
    last_seen_at          TIMESTAMPTZ   NOT NULL,
    open                  BOOLEAN       NOT NULL,
    CONSTRAINT uq_posting_source_external UNIQUE (source, external_id)
);

-- The feed sorts open postings newest-first; this index serves that query directly.
CREATE INDEX idx_posting_open_first_seen ON posting (open, first_seen_at DESC);
