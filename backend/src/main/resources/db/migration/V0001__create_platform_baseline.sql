CREATE TABLE intf_interface_definition (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    interface_code NVARCHAR(64) NOT NULL,
    interface_version NVARCHAR(32) NOT NULL,
    interface_name NVARCHAR(200) NOT NULL,
    source_system_code NVARCHAR(64) NOT NULL,
    target_system_code NVARCHAR(64) NOT NULL,
    enabled BIT NOT NULL CONSTRAINT df_intf_definition_enabled DEFAULT 0,
    contract_checksum NVARCHAR(128) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_intf_definition_created DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_intf_definition_updated DEFAULT SYSUTCDATETIME(),
    row_version ROWVERSION,
    CONSTRAINT uq_intf_definition_code_version UNIQUE (interface_code, interface_version)
);

CREATE TABLE exch_exchange_record (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    request_id NVARCHAR(64) NOT NULL,
    trace_id NVARCHAR(64) NULL,
    idempotency_key NVARCHAR(160) NOT NULL,
    interface_code NVARCHAR(64) NOT NULL,
    interface_version NVARCHAR(32) NOT NULL,
    source_system_code NVARCHAR(64) NOT NULL,
    target_system_code NVARCHAR(64) NOT NULL,
    organization_code NVARCHAR(64) NOT NULL,
    source_record_id NVARCHAR(128) NOT NULL,
    status NVARCHAR(32) NOT NULL,
    result_code NVARCHAR(64) NULL,
    result_message NVARCHAR(500) NULL,
    business_occurred_at DATETIME2(3) NULL,
    received_at DATETIME2(3) NOT NULL CONSTRAINT df_exch_record_received DEFAULT SYSUTCDATETIME(),
    processed_at DATETIME2(3) NULL,
    retry_count INT NOT NULL CONSTRAINT df_exch_record_retry DEFAULT 0,
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_exch_record_created DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_exch_record_updated DEFAULT SYSUTCDATETIME(),
    row_version ROWVERSION,
    CONSTRAINT uq_exch_record_request UNIQUE (request_id),
    CONSTRAINT uq_exch_record_idempotency UNIQUE (interface_code, organization_code, idempotency_key),
    CONSTRAINT ck_exch_record_retry_nonnegative CHECK (retry_count >= 0)
);

CREATE INDEX ix_exch_record_org_received
    ON exch_exchange_record (organization_code, received_at DESC)
    INCLUDE (interface_code, source_record_id, status, processed_at);

CREATE INDEX ix_exch_record_status_received
    ON exch_exchange_record (status, received_at)
    INCLUDE (organization_code, interface_code, retry_count);

CREATE TABLE audit_operation_event (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    event_type NVARCHAR(64) NOT NULL,
    actor_id NVARCHAR(128) NOT NULL,
    actor_organization_code NVARCHAR(64) NULL,
    action_name NVARCHAR(128) NOT NULL,
    target_type NVARCHAR(64) NOT NULL,
    target_id NVARCHAR(160) NULL,
    request_id NVARCHAR(64) NULL,
    trace_id NVARCHAR(64) NULL,
    result_code NVARCHAR(64) NOT NULL,
    occurred_at DATETIME2(3) NOT NULL CONSTRAINT df_audit_event_occurred DEFAULT SYSUTCDATETIME()
);

CREATE INDEX ix_audit_event_occurred
    ON audit_operation_event (occurred_at DESC)
    INCLUDE (event_type, actor_id, target_type, target_id, result_code);
