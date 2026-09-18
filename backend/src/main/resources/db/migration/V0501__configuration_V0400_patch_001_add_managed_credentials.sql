-- 业务域: configuration
-- 脚本类型: PATCH
-- 归属主版本: V0400
-- 补丁序号: 001
-- 变更说明: 为机构外部系统连接增加由平台托管的加密认证信息
-- 需求依据: 基层HIS机构连接需要由管理端直接配置厂商编号、账号、密码和机构授权码
-- 数据边界: 只保存应用层AES-GCM密文、随机向量和密钥版本，不保存或回显任何明文认证信息
-- 时间规则: created_at和updated_at保存UTC时间，精度为毫秒
-- 回退方案: 回退前先停用依赖托管认证的连接，再删除sys_external_endpoint_credential表；原服务地址资料不受影响

-- 表中文名称: 外部连接认证信息表
-- 表用途: 保存机构外部系统连接的应用层加密认证信息；不保存明文密码、授权码或接口业务报文
CREATE TABLE sys_external_endpoint_credential (
    endpoint_id BIGINT NOT NULL CONSTRAINT pk_sys_external_endpoint_credential PRIMARY KEY, -- 服务地址主键：每条机构连接最多一组托管认证信息
    encrypted_payload VARBINARY(2000) NOT NULL, -- 加密内容：AES-GCM密文及认证标签，解密后仅在单次调用内短暂使用
    initialization_vector VARBINARY(12) NOT NULL, -- 随机向量：每次保存重新生成的12字节AES-GCM随机向量
    encryption_version SMALLINT NOT NULL CONSTRAINT df_sys_external_endpoint_credential_version DEFAULT 1, -- 加密版本：当前固定为1，供后续安全轮换识别
    created_by NVARCHAR(64) NOT NULL, -- 创建人标识：首次保存认证信息的平台登录名快照
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_external_endpoint_credential_created DEFAULT SYSUTCDATETIME(), -- 创建时间：认证信息创建UTC时间，精度为毫秒
    updated_by NVARCHAR(64) NOT NULL, -- 修改人标识：最后替换认证信息的平台登录名快照
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_sys_external_endpoint_credential_updated DEFAULT SYSUTCDATETIME(), -- 修改时间：认证信息最后替换UTC时间，精度为毫秒
    row_version ROWVERSION, -- 并发版本：由数据库生成，仅用于防止并发覆盖
    CONSTRAINT fk_sys_external_endpoint_credential_endpoint FOREIGN KEY (endpoint_id) REFERENCES sys_external_endpoint(id), -- 保证认证信息只能属于已登记的服务地址
    CONSTRAINT ck_sys_external_endpoint_credential_payload CHECK (DATALENGTH(encrypted_payload) BETWEEN 17 AND 2000), -- 保证密文至少包含GCM认证标签且不超过受控长度
    CONSTRAINT ck_sys_external_endpoint_credential_iv CHECK (DATALENGTH(initialization_vector) = 12), -- 保证随机向量符合当前AES-GCM约定
    CONSTRAINT ck_sys_external_endpoint_credential_version CHECK (encryption_version = 1) -- 当前只接受已实现的第一版加密格式
);

DECLARE @object_descriptions TABLE (
    object_type NVARCHAR(16) NOT NULL,
    table_name SYSNAME NOT NULL,
    object_name SYSNAME NULL,
    description NVARCHAR(1000) NOT NULL
);

INSERT INTO @object_descriptions (object_type, table_name, object_name, description)
VALUES
    (N'TABLE', N'sys_external_endpoint_credential', NULL, N'外部连接认证信息表'),
    (N'COLUMN', N'sys_external_endpoint_credential', N'endpoint_id', N'服务地址主键'),
    (N'COLUMN', N'sys_external_endpoint_credential', N'encrypted_payload', N'认证信息密文'),
    (N'COLUMN', N'sys_external_endpoint_credential', N'initialization_vector', N'加密随机向量'),
    (N'COLUMN', N'sys_external_endpoint_credential', N'encryption_version', N'加密版本'),
    (N'COLUMN', N'sys_external_endpoint_credential', N'created_by', N'创建人标识'),
    (N'COLUMN', N'sys_external_endpoint_credential', N'created_at', N'创建时间'),
    (N'COLUMN', N'sys_external_endpoint_credential', N'updated_by', N'修改人标识'),
    (N'COLUMN', N'sys_external_endpoint_credential', N'updated_at', N'修改时间'),
    (N'COLUMN', N'sys_external_endpoint_credential', N'row_version', N'并发版本'),
    (N'CONSTRAINT', N'sys_external_endpoint_credential', N'pk_sys_external_endpoint_credential', N'保证每条服务地址最多保存一组托管认证信息。'),
    (N'CONSTRAINT', N'sys_external_endpoint_credential', N'fk_sys_external_endpoint_credential_endpoint', N'保证认证信息属于已登记的服务地址。'),
    (N'CONSTRAINT', N'sys_external_endpoint_credential', N'ck_sys_external_endpoint_credential_payload', N'限制认证密文长度。'),
    (N'CONSTRAINT', N'sys_external_endpoint_credential', N'ck_sys_external_endpoint_credential_iv', N'限制AES-GCM随机向量为12字节。'),
    (N'CONSTRAINT', N'sys_external_endpoint_credential', N'ck_sys_external_endpoint_credential_version', N'限制为平台支持的加密版本。'),
    (N'CONSTRAINT', N'sys_external_endpoint_credential', N'df_sys_external_endpoint_credential_version', N'默认使用第一版加密格式。'),
    (N'CONSTRAINT', N'sys_external_endpoint_credential', N'df_sys_external_endpoint_credential_created', N'默认使用SQL Server当前UTC时间作为创建时间。'),
    (N'CONSTRAINT', N'sys_external_endpoint_credential', N'df_sys_external_endpoint_credential_updated', N'默认使用SQL Server当前UTC时间作为修改时间。');

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
    IF @object_type = N'TABLE'
        EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description,
            @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name;
    ELSE
        EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=@description,
            @level0type=N'SCHEMA', @level0name=N'dbo', @level1type=N'TABLE', @level1name=@table_name,
            @level2type=@object_type, @level2name=@object_name;

    FETCH NEXT FROM description_cursor INTO @object_type, @table_name, @object_name, @description;
END;

CLOSE description_cursor;
DEALLOCATE description_cursor;
