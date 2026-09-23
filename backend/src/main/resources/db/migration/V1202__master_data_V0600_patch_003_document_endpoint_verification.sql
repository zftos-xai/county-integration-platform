-- 业务域: master_data
-- 脚本类型: PATCH
-- 归属主版本: V0600
-- 补丁序号: 003
-- 变更说明: 为既有100-008端点校验字段和约束补写数据库元数据说明，不重写已执行的V0600。
-- 需求依据: V0600已在开发库执行，新增端点校验对象缺少可落库的MS_Description；已执行迁移校验和必须保持不变。
-- 数据边界: 仅更新SQL Server对象说明，不读取或修改端点配置、授权码和业务数据。
-- 时间规则: 仅说明既有verified_at保存UTC时间，不转换数据库时间值。
-- 回退方案: 停用依赖元数据说明的导出流程；完成变更审批后删除本补丁新增的MS_Description属性。

DECLARE @object_descriptions TABLE (object_type NVARCHAR(16), table_name SYSNAME, object_name SYSNAME, description NVARCHAR(1000));
INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'COLUMN', N'sys_external_endpoint', N'organization_query_name', N'机构查询名称'),
    (N'COLUMN', N'sys_external_endpoint', N'source_organization_id', N'已确认来源机构标识'),
    (N'COLUMN', N'sys_external_endpoint', N'source_organization_name', N'已确认来源机构名称'),
    (N'COLUMN', N'sys_external_endpoint', N'verification_status', N'机构校验状态'),
    (N'COLUMN', N'sys_external_endpoint', N'verified_at', N'校验完成时间（UTC）'),
    (N'COLUMN', N'sys_external_endpoint', N'verification_failure_summary', N'校验失败摘要（不含地址、凭证和原始报文）'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'df_sys_external_endpoint_verification_status', N'新服务地址的机构校验状态默认为未校验。'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'ck_sys_external_endpoint_verification_status', N'限定机构校验状态为未校验、已确认、失败或结果未知。'),
    (N'CONSTRAINT', N'sys_external_endpoint', N'ck_sys_external_endpoint_verification_fact', N'已确认状态必须具备来源机构信息和校验时间且已启用；其他状态必须停用。');

DECLARE @object_type NVARCHAR(16), @table_name SYSNAME, @object_name SYSNAME, @description NVARCHAR(1000);
DECLARE description_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT object_type, table_name, object_name, description FROM @object_descriptions;
OPEN description_cursor;
FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;
WHILE @@FETCH_STATUS = 0
BEGIN
    EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description,
        @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name,
        @level2type=@object_type, @level2name=@object_name;
    FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;
END;
CLOSE description_cursor;
DEALLOCATE description_cursor;
