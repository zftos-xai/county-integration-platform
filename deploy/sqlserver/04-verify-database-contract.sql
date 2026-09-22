-- 用途: 在SSMS或sqlcmd中直接执行，只读核对当前数据库与Flyway物理字段契约。
-- 生成来源: backend/src/main/resources/db/migration/V*.sql。请勿手工维护预期字段清单。
-- 重新生成: node tools/generate-database-contract-sql.mjs
-- 数据边界: 不读取业务数据，不修改表、字段、约束、索引或数据。
-- 判定原则: Flyway迁移是结构权威来源；数据库不一致标记为DATABASE，额外字段标记为MANUAL_REVIEW。

USE [county_integration];
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @expected_columns TABLE (
    schema_name sysname NOT NULL,
    table_name sysname NOT NULL,
    column_name sysname NOT NULL,
    type_name sysname NOT NULL,
    max_length smallint NULL,
    numeric_precision tinyint NULL,
    numeric_scale tinyint NULL,
    is_nullable bit NOT NULL,
    is_identity bit NOT NULL,
    is_computed bit NOT NULL,
    PRIMARY KEY (schema_name, table_name, column_name)
);

INSERT INTO @expected_columns (
    schema_name, table_name, column_name, type_name, max_length, numeric_precision,
    numeric_scale, is_nullable, is_identity, is_computed
)
VALUES
    (N'dbo', N'exch_exchange_record', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'exch_exchange_record', N'request_id', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'exch_exchange_record', N'interface_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'exch_exchange_record', N'source_system_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'exch_exchange_record', N'target_system_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'exch_exchange_record', N'organization_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'exch_exchange_record', N'source_record_id', N'nvarchar', 256, NULL, NULL, 0, 0, 0),
    (N'dbo', N'exch_exchange_record', N'exchange_result', N'nvarchar', 32, NULL, NULL, 0, 0, 0),
    (N'dbo', N'exch_exchange_record', N'result_code', N'nvarchar', 128, NULL, NULL, 1, 0, 0),
    (N'dbo', N'exch_exchange_record', N'result_message', N'nvarchar', 1000, NULL, NULL, 1, 0, 0),
    (N'dbo', N'exch_exchange_record', N'duration_ms', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'exch_exchange_record', N'request_summary', N'nvarchar', 1000, NULL, NULL, 1, 0, 0),
    (N'dbo', N'exch_exchange_record', N'communication_error_summary', N'nvarchar', 1000, NULL, NULL, 1, 0, 0),
    (N'dbo', N'exch_exchange_record', N'received_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'exch_exchange_record', N'processed_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'exch_exchange_record', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'exch_exchange_record', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'org_organization', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'org_organization', N'organization_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'org_organization', N'organization_name', N'nvarchar', 400, NULL, NULL, 0, 0, 0),
    (N'dbo', N'org_organization', N'organization_type', N'nvarchar', 64, NULL, NULL, 0, 0, 0),
    (N'dbo', N'org_organization', N'parent_id', N'bigint', NULL, NULL, NULL, 1, 0, 0),
    (N'dbo', N'org_organization', N'is_enabled', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'org_organization', N'valid_from', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'org_organization', N'valid_to', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'org_organization', N'created_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'org_organization', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'org_organization', N'updated_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'org_organization', N'updated_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'org_organization', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'sys_user', N'login_name', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user', N'display_name', N'nvarchar', 200, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user', N'password_hash', N'nvarchar', 200, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user', N'primary_organization_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user', N'is_enabled', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user', N'must_change_password', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user', N'password_changed_at', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'sys_user', N'created_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_user', N'updated_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user', N'updated_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_user', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_role', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'sys_role', N'role_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_role', N'role_name', N'nvarchar', 200, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_role', N'is_enabled', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_role', N'is_system_managed', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_role', N'created_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_role', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_role', N'updated_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_role', N'updated_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_role', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_permission', N'permission_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_permission', N'permission_name', N'nvarchar', 200, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_permission', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_user_role', N'user_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user_role', N'role_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user_role', N'granted_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user_role', N'granted_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_role_permission', N'role_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_role_permission', N'permission_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_role_permission', N'granted_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_role_permission', N'granted_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_user_organization_scope', N'user_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user_organization_scope', N'organization_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user_organization_scope', N'granted_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_user_organization_scope', N'granted_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_parameter_value', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'sys_parameter_value', N'parameter_key', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_parameter_value', N'value_type', N'nvarchar', 32, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_parameter_value', N'environment_code', N'nvarchar', 32, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_parameter_value', N'organization_id', N'bigint', NULL, NULL, NULL, 1, 0, 0),
    (N'dbo', N'sys_parameter_value', N'parameter_value', N'nvarchar', 2000, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_parameter_value', N'is_enabled', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_parameter_value', N'created_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_parameter_value', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_parameter_value', N'updated_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_parameter_value', N'updated_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_parameter_value', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_type', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'sys_dictionary_type', N'type_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_type', N'type_name', N'nvarchar', 200, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_type', N'description', N'nvarchar', 1000, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_type', N'is_enabled', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_type', N'created_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_type', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_dictionary_type', N'updated_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_type', N'updated_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_dictionary_type', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_item', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'sys_dictionary_item', N'dictionary_type_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_item', N'item_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_item', N'item_label', N'nvarchar', 400, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_item', N'sort_order', N'int', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_item', N'is_enabled', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_item', N'created_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_item', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_dictionary_item', N'updated_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_dictionary_item', N'updated_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_dictionary_item', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_system', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'sys_external_system', N'system_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_system', N'system_name', N'nvarchar', 200, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_system', N'description', N'nvarchar', 1000, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_system', N'is_enabled', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_system', N'created_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_system', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_external_system', N'updated_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_system', N'updated_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_external_system', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'sys_external_endpoint', N'external_system_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'environment_code', N'nvarchar', 32, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'organization_id', N'bigint', NULL, NULL, NULL, 1, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'base_url', N'nvarchar', 1000, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'connect_timeout_ms', N'int', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'read_timeout_ms', N'int', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'credential_reference', N'nvarchar', 400, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'is_enabled', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'created_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'updated_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'updated_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'audit_management_event', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'audit_management_event', N'occurred_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'audit_management_event', N'actor_user_id', N'bigint', NULL, NULL, NULL, 1, 0, 0),
    (N'dbo', N'audit_management_event', N'actor_login_snapshot', N'nvarchar', 256, NULL, NULL, 0, 0, 0),
    (N'dbo', N'audit_management_event', N'organization_id', N'bigint', NULL, NULL, NULL, 1, 0, 0),
    (N'dbo', N'audit_management_event', N'organization_code_snapshot', N'nvarchar', 128, NULL, NULL, 1, 0, 0),
    (N'dbo', N'audit_management_event', N'action_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'audit_management_event', N'target_type', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'audit_management_event', N'target_id', N'nvarchar', 256, NULL, NULL, 0, 0, 0),
    (N'dbo', N'audit_management_event', N'result_code', N'nvarchar', 32, NULL, NULL, 0, 0, 0),
    (N'dbo', N'audit_management_event', N'change_summary', N'nvarchar', 2000, NULL, NULL, 0, 0, 0),
    (N'dbo', N'audit_management_event', N'request_id', N'nvarchar', 128, NULL, NULL, 1, 0, 0),
    (N'dbo', N'sys_external_endpoint_credential', N'endpoint_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint_credential', N'encrypted_payload', N'varbinary', 2000, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint_credential', N'initialization_vector', N'varbinary', 12, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint_credential', N'encryption_version', N'smallint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint_credential', N'created_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint_credential', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint_credential', N'updated_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint_credential', N'updated_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint_credential', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'md_sync_batch', N'batch_no', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'request_key', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'source_system_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'scope_type', N'nvarchar', 32, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'organization_id', N'bigint', NULL, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'environment_code', N'nvarchar', 32, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'data_category', N'nvarchar', 64, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'sync_mode', N'nvarchar', 40, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'source_type', N'nvarchar', 32, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'source_organization_id', N'nvarchar', 256, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'source_endpoint_id', N'bigint', NULL, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'source_endpoint_version', N'varbinary', 8, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'full_rule_evidence', N'nvarchar', 1000, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'query_started_at', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'query_ended_at', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'diagnosis_category', N'nvarchar', 64, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'diagnosis_version', N'nvarchar', 128, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'data_trade_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'count_trade_code', N'nvarchar', 128, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'batch_status', N'nvarchar', 64, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'is_active', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'declared_count', N'bigint', NULL, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'returned_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'duplicate_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'invalid_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'conflict_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'created_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'updated_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'unchanged_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'source_missing_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'active_count', N'bigint', NULL, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'started_at', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'finished_at', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'completed_at', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'failure_code', N'nvarchar', 128, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'failure_summary', N'nvarchar', 1000, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_sync_batch', N'created_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'updated_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'updated_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'md_sync_batch', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'md_hospital_directory', N'organization_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory', N'source_system_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory', N'directory_type', N'nvarchar', 32, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory', N'source_record_code', N'nvarchar', 100, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory', N'source_record_name', N'nvarchar', 100, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory', N'mnemonic_code', N'nvarchar', 40, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_hospital_directory', N'category_name', N'nvarchar', 40, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_hospital_directory', N'remark', N'nvarchar', 200, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_hospital_directory', N'department_code', N'nvarchar', 100, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_hospital_directory', N'department_name', N'nvarchar', 100, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_hospital_directory', N'ward_name', N'nvarchar', 100, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_hospital_directory', N'source_organization_code', N'nvarchar', 100, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_hospital_directory', N'source_enabled_flag', N'nvarchar', 100, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_hospital_directory', N'is_valid', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory', N'invalidated_at', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'md_hospital_directory', N'invalid_reason', N'nvarchar', 128, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_hospital_directory', N'first_seen_batch_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory', N'latest_batch_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory', N'first_seen_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'md_hospital_directory', N'last_seen_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'md_hospital_directory', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'md_hospital_directory', N'updated_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'md_hospital_directory', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_relation', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'md_hospital_directory_relation', N'organization_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_relation', N'source_system_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_relation', N'relation_type', N'nvarchar', 64, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_relation', N'source_record_code', N'nvarchar', 100, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_relation', N'target_record_code', N'nvarchar', 100, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_relation', N'latest_batch_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_relation', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'organization_query_name', N'nvarchar', 200, NULL, NULL, 1, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'source_organization_id', N'nvarchar', 256, NULL, NULL, 1, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'source_organization_name', N'nvarchar', 400, NULL, NULL, 1, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'verification_status', N'nvarchar', 64, NULL, NULL, 0, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'verified_at', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'sys_external_endpoint', N'verification_failure_summary', N'nvarchar', 1000, NULL, NULL, 1, 0, 0),
    (N'dbo', N'db_contract_plan', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'db_contract_plan', N'plan_no', N'nvarchar', 80, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan', N'status', N'nvarchar', 48, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan', N'issue_count', N'int', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan', N'executable_count', N'int', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan', N'summary', N'nvarchar', 1000, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan', N'created_by', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'db_contract_plan', N'approved_by', N'nvarchar', 128, NULL, NULL, 1, 0, 0),
    (N'dbo', N'db_contract_plan', N'approved_at', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'db_contract_plan', N'approval_note', N'nvarchar', 1000, NULL, NULL, 1, 0, 0),
    (N'dbo', N'db_contract_plan', N'executed_by', N'nvarchar', 128, NULL, NULL, 1, 0, 0),
    (N'dbo', N'db_contract_plan', N'executed_at', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'db_contract_plan', N'verified_at', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'db_contract_plan', N'failure_message', N'nvarchar', 2000, NULL, NULL, 1, 0, 0),
    (N'dbo', N'db_contract_plan', N'updated_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'db_contract_plan', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan_item', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'db_contract_plan_item', N'plan_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan_item', N'violation_code', N'nvarchar', 64, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan_item', N'object_name', N'nvarchar', 600, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan_item', N'expected_value', N'nvarchar', 2000, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan_item', N'actual_value', N'nvarchar', 2000, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan_item', N'direction', N'nvarchar', 48, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan_item', N'action_text', N'nvarchar', 2000, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan_item', N'executable', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan_item', N'ddl_preview', N'nvarchar', 4000, NULL, NULL, 1, 0, 0),
    (N'dbo', N'db_contract_plan_item', N'execution_note', N'nvarchar', 1000, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan_item', N'execution_status', N'nvarchar', 48, NULL, NULL, 0, 0, 0),
    (N'dbo', N'db_contract_plan_item', N'verification_status', N'nvarchar', 48, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'batch_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'directory_type', N'int', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'outcome_status', N'nvarchar', 64, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'returned_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'duplicate_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'invalid_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'conflict_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'created_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'updated_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'unchanged_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'source_missing_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'active_count', N'bigint', NULL, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'failure_summary', N'nvarchar', 1000, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_hospital_directory_sync_result', N'finished_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'id', N'bigint', NULL, NULL, NULL, 0, 1, 0),
    (N'dbo', N'md_medical_directory', N'organization_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'source_system_code', N'nvarchar', 128, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'directory_type', N'nvarchar', 32, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'source_record_code', N'nvarchar', 200, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'source_record_name', N'nvarchar', 600, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'mnemonic_code', N'nvarchar', 200, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'category_name', N'nvarchar', 400, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'unit', N'nvarchar', 200, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'specification', N'nvarchar', 1000, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'dosage_form', N'nvarchar', 200, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'manufacturer_name', N'nvarchar', 600, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'remark', N'nvarchar', 1000, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'source_created_at', N'nvarchar', 200, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'package_unit', N'nvarchar', 200, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'conversion_factor', N'nvarchar', 200, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'approval_number', N'nvarchar', 200, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'standard_code', N'nvarchar', 200, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'package_material', N'nvarchar', 200, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'processing_method', N'nvarchar', 200, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'region', N'nvarchar', 200, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'category', N'nvarchar', 200, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'source_enabled_flag', N'nvarchar', 200, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'is_valid', N'bit', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'invalidated_at', N'datetime2', NULL, NULL, 3, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'invalid_reason', N'nvarchar', 128, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory', N'first_seen_batch_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'latest_batch_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'first_seen_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'last_seen_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'created_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'updated_at', N'datetime2', NULL, NULL, 3, 0, 0, 0),
    (N'dbo', N'md_medical_directory', N'row_version', N'rowversion', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'batch_id', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'directory_type', N'nvarchar', 32, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'outcome_status', N'nvarchar', 64, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'declared_count', N'bigint', NULL, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'returned_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'duplicate_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'invalid_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'conflict_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'created_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'updated_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'unchanged_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'source_missing_count', N'bigint', NULL, NULL, NULL, 0, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'active_count', N'bigint', NULL, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'failure_summary', N'nvarchar', 1000, NULL, NULL, 1, 0, 0),
    (N'dbo', N'md_medical_directory_sync_result', N'finished_at', N'datetime2', NULL, NULL, 3, 0, 0, 0);

DECLARE @violations TABLE (
    error_code nvarchar(32) NOT NULL,
    direction nvarchar(32) NOT NULL,
    object_name nvarchar(776) NOT NULL,
    expected_value nvarchar(4000) NOT NULL,
    actual_value nvarchar(4000) NOT NULL,
    recommendation nvarchar(4000) NOT NULL
);

DECLARE @product_version nvarchar(128);
DECLARE @major_version int;
DECLARE @compatibility_level int;

SET @product_version = CONVERT(nvarchar(128), SERVERPROPERTY('ProductVersion'));
SET @major_version = CONVERT(int, LEFT(@product_version, CHARINDEX(N'.', @product_version) - 1));
SELECT @compatibility_level = [compatibility_level]
FROM sys.databases
WHERE [name] = DB_NAME();

IF @major_version <> 11
BEGIN
    INSERT INTO @violations VALUES (
        N'DBCONTRACT-E001', N'DATABASE', N'SQL Server实例版本', N'SQL Server 2012，主版本11',
        @product_version, N'切换到医院批准的SQL Server 2012 SP4实例'
    );
END;

IF @compatibility_level <> 110
BEGIN
    INSERT INTO @violations VALUES (
        N'DBCONTRACT-E002', N'DATABASE', N'数据库兼容级别', N'110',
        CONVERT(nvarchar(20), @compatibility_level), N'由DBA评估后将当前平台数据库兼容级别调整为110'
    );
END;

;WITH actual_columns AS (
    SELECT
        table_schema.[name] AS schema_name,
        business_table.[name] AS table_name,
        business_column.[name] AS column_name,
        CASE WHEN column_type.[name] = N'timestamp' THEN N'rowversion' ELSE column_type.[name] END AS type_name,
        business_column.[max_length],
        business_column.[precision] AS numeric_precision,
        business_column.[scale] AS numeric_scale,
        business_column.[is_nullable],
        business_column.[is_identity],
        business_column.[is_computed]
    FROM sys.tables AS business_table
    INNER JOIN sys.schemas AS table_schema ON table_schema.[schema_id] = business_table.[schema_id]
    INNER JOIN sys.columns AS business_column ON business_column.[object_id] = business_table.[object_id]
    INNER JOIN sys.types AS column_type ON column_type.[user_type_id] = business_column.[user_type_id]
    WHERE EXISTS (
        SELECT 1 FROM @expected_columns AS expected_table
        WHERE expected_table.schema_name = table_schema.[name]
          AND expected_table.table_name = business_table.[name]
    )
)
INSERT INTO @violations
SELECT
    CASE
        WHEN actual.column_name IS NULL THEN N'DBCONTRACT-E101'
        WHEN expected.type_name <> actual.type_name THEN N'DBCONTRACT-E102'
        WHEN expected.max_length IS NOT NULL AND expected.max_length <> actual.max_length THEN N'DBCONTRACT-E103'
        WHEN expected.numeric_precision IS NOT NULL AND expected.numeric_precision <> actual.numeric_precision THEN N'DBCONTRACT-E104'
        WHEN expected.numeric_scale IS NOT NULL AND expected.numeric_scale <> actual.numeric_scale THEN N'DBCONTRACT-E105'
        WHEN expected.is_nullable <> actual.is_nullable THEN N'DBCONTRACT-E106'
        WHEN expected.is_identity <> actual.is_identity THEN N'DBCONTRACT-E107'
        ELSE N'DBCONTRACT-E108'
    END,
    N'DATABASE',
    expected.schema_name + N'.' + expected.table_name + N'.' + expected.column_name,
    N'type=' + expected.type_name
        + N'; max_length=' + COALESCE(CONVERT(nvarchar(20), expected.max_length), N'N/A')
        + N'; precision=' + COALESCE(CONVERT(nvarchar(20), expected.numeric_precision), N'N/A')
        + N'; scale=' + COALESCE(CONVERT(nvarchar(20), expected.numeric_scale), N'N/A')
        + N'; nullable=' + CONVERT(nvarchar(1), expected.is_nullable)
        + N'; identity=' + CONVERT(nvarchar(1), expected.is_identity)
        + N'; computed=' + CONVERT(nvarchar(1), expected.is_computed),
    CASE WHEN actual.column_name IS NULL THEN N'字段不存在' ELSE
        N'type=' + actual.type_name
        + N'; max_length=' + CONVERT(nvarchar(20), actual.max_length)
        + N'; precision=' + CONVERT(nvarchar(20), actual.numeric_precision)
        + N'; scale=' + CONVERT(nvarchar(20), actual.numeric_scale)
        + N'; nullable=' + CONVERT(nvarchar(1), actual.is_nullable)
        + N'; identity=' + CONVERT(nvarchar(1), actual.is_identity)
        + N'; computed=' + CONVERT(nvarchar(1), actual.is_computed) END,
    N'核对需求后通过正式Flyway迁移修正数据库；不要只修改Mapper或Java类型掩盖物理结构差异'
FROM @expected_columns AS expected
LEFT JOIN actual_columns AS actual
    ON actual.schema_name = expected.schema_name
   AND actual.table_name = expected.table_name
   AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL
   OR expected.type_name <> actual.type_name
   OR (expected.max_length IS NOT NULL AND expected.max_length <> actual.max_length)
   OR (expected.numeric_precision IS NOT NULL AND expected.numeric_precision <> actual.numeric_precision)
   OR (expected.numeric_scale IS NOT NULL AND expected.numeric_scale <> actual.numeric_scale)
   OR expected.is_nullable <> actual.is_nullable
   OR expected.is_identity <> actual.is_identity
   OR expected.is_computed <> actual.is_computed;

;WITH expected_tables AS (
    SELECT DISTINCT schema_name, table_name FROM @expected_columns
+)
INSERT INTO @violations
SELECT
    N'DBCONTRACT-E109', N'MANUAL_REVIEW',
    table_schema.[name] + N'.' + business_table.[name] + N'.' + business_column.[name],
    N'当前Flyway迁移中不存在该字段',
    N'type=' + column_type.[name] + N'; max_length=' + CONVERT(nvarchar(20), business_column.[max_length]),
    N'确认是否为未经Flyway执行的人工改表；需要保留时新增正式迁移和契约依据'
FROM sys.tables AS business_table
INNER JOIN sys.schemas AS table_schema ON table_schema.[schema_id] = business_table.[schema_id]
INNER JOIN sys.columns AS business_column ON business_column.[object_id] = business_table.[object_id]
INNER JOIN sys.types AS column_type ON column_type.[user_type_id] = business_column.[user_type_id]
INNER JOIN expected_tables AS expected_table
    ON expected_table.schema_name = table_schema.[name]
   AND expected_table.table_name = business_table.[name]
LEFT JOIN @expected_columns AS expected
    ON expected.schema_name = table_schema.[name]
   AND expected.table_name = business_table.[name]
   AND expected.column_name = business_column.[name]
WHERE expected.column_name IS NULL;

SELECT
    error_code, direction, object_name, expected_value, actual_value, recommendation
FROM @violations
ORDER BY object_name, error_code;

IF EXISTS (SELECT 1 FROM @violations)
BEGIN
    DECLARE @violation_count int;
    DECLARE @failure_message nvarchar(2048);
    SELECT @violation_count = COUNT(*) FROM @violations;
    SET @failure_message = N'数据库契约检查失败，共发现 ' + CONVERT(nvarchar(20), @violation_count)
        + N' 项差异；请按结果集中的 direction 和 recommendation 处理。';
    RAISERROR(@failure_message, 16, 1);
END;
ELSE
BEGIN
    SELECT
        N'PASS' AS contract_status,
        @product_version AS product_version,
        @compatibility_level AS compatibility_level,
        (SELECT COUNT(*) FROM @expected_columns) AS verified_column_count,
        N'数据库物理字段与Flyway契约一致；Mapper和Java模型仍由应用启动检查负责。' AS message;
END;
GO
