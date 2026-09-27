-- 业务域: configuration
-- 脚本类型: PATCH
-- 归属主版本: V0400
-- 补丁序号: 003
-- 变更说明: 为外部系统登记增加县医院调用平台所需的入站Key单向校验值
-- 需求依据: LIS平台侧适配调用链设计第8节；县医院调用方通过外部系统代码和Key被平台识别
-- 数据边界: 仅保存BCrypt单向校验值，不保存或返回明文Key；出站端点凭证仍由独立凭证表管理
-- 时间规则: 外部系统修改时间继续由sys_external_system.updated_at使用UTC毫秒时间记录
-- 回退方案: 停止依赖县医院入站调用后，删除入站Key列及对应MS_Description；外部系统和出站端点配置不受影响

-- 为系统级入站认证增加单向校验值；出站端点认证仍保留原有独立存储。
ALTER TABLE sys_external_system
    ADD
        inbound_key_hash NVARCHAR(100) NULL; -- 入站Key单向校验值：未配置时该外部系统不能调用平台集成入口
GO

-- 限定已配置Key哈希非空白且长度不超过受控列长。
ALTER TABLE sys_external_system
    ADD CONSTRAINT ck_sys_external_system_inbound_key_hash
    CHECK (inbound_key_hash IS NULL OR LEN(LTRIM(RTRIM(inbound_key_hash))) BETWEEN 1 AND 100);

DECLARE @object_descriptions TABLE (
    object_type NVARCHAR(16) NOT NULL,
    table_name SYSNAME NOT NULL,
    object_name SYSNAME NULL,
    description NVARCHAR(1000) NOT NULL
);

INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'COLUMN', N'sys_external_system', N'inbound_key_hash', N'入站Key校验值'),
    (N'CONSTRAINT', N'sys_external_system', N'ck_sys_external_system_inbound_key_hash', N'限制入站Key校验值为空或为非空白且长度不超过100个字符。');

DECLARE @object_type NVARCHAR(16);
DECLARE @table_name SYSNAME;
DECLARE @object_name SYSNAME;
DECLARE @description NVARCHAR(1000);

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
