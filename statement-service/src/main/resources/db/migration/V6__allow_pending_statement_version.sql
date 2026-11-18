ALTER TABLE statements DROP CONSTRAINT chk_statement_version_status;
ALTER TABLE statements ADD CONSTRAINT chk_statement_version_status
    CHECK (version_status IN ('PENDING', 'CURRENT', 'SUPERSEDED', 'FAILED'));
