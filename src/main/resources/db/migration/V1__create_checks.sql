CREATE TABLE checks (
    id uuid PRIMARY KEY,
    record_type varchar(16) NOT NULL CHECK (record_type IN ('DAILY', 'WEEKLY')),
    status varchar(32) NOT NULL CHECK (status IN ('CHECK_IN_PROGRESS', 'COMPLETE', 'INCOMPLETE')),
    checked_at timestamp with time zone NOT NULL,
    status_label text NOT NULL CHECK (btrim(status_label) <> ''),
    reason text NOT NULL CHECK (btrim(reason) <> '')
);

CREATE TABLE check_documents (
    id uuid PRIMARY KEY,
    check_id uuid NOT NULL REFERENCES checks(id) ON DELETE CASCADE,
    position integer NOT NULL CHECK (position >= 0),
    name varchar(255) NOT NULL CHECK (btrim(name) <> ''),
    detected_type varchar(32) CHECK (detected_type IN (
        'OBSERVATION_DIARY', 'SESSION_REPORT', 'PARENT_FEEDBACK', 'SPECIALIST_CONCLUSION'
    )),
    size_bytes bigint NOT NULL CHECK (size_bytes >= 0),
    CONSTRAINT uk_check_document_position UNIQUE (check_id, position)
);

CREATE TABLE check_issues (
    id uuid PRIMARY KEY,
    check_id uuid NOT NULL REFERENCES checks(id) ON DELETE CASCADE,
    position integer NOT NULL CHECK (position >= 0),
    level varchar(16) NOT NULL CHECK (level IN ('WARNING', 'ERROR')),
    message text NOT NULL CHECK (btrim(message) <> ''),
    document_index integer CHECK (document_index >= 0),
    CONSTRAINT uk_check_issue_position UNIQUE (check_id, position),
    CONSTRAINT fk_issue_document FOREIGN KEY (check_id, document_index)
        REFERENCES check_documents(check_id, position) DEFERRABLE INITIALLY DEFERRED
);
