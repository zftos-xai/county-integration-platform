-- 业务域: primary_his_system_seed
-- 主版本: V0800
-- 脚本类型: MAIN
-- 变更说明: 初始化固定的基层HIS系统身份，取消由管理员手工登记系统的错误前置步骤
-- 需求依据: 基层HIS是当前平台固定对接边界，管理员只维护各机构接口配置，不应手工创建系统身份
-- 数据边界: 仅登记系统代码、名称和用途；不写入任何机构地址、账号、密码或授权信息
-- 时间规则: 记录创建和更新时间均使用sys_external_system默认UTC时间；应用层负责本地化展示
-- 回退方案: 在尚未配置机构接口时，可删除system_code为PRIMARY_HIS的系统记录；已配置接口时保留系统记录并仅停用

-- 使用稳定代码保证反复部署或已有人工登记时不会产生重复系统。
IF NOT EXISTS (
    SELECT 1
    FROM sys_external_system
    WHERE system_code = N'PRIMARY_HIS'
)
BEGIN
    INSERT INTO sys_external_system (
        system_code, -- 固定基层HIS系统代码，供运行时选择协议适配器
        system_name, -- 管理端展示名称
        description, -- 非敏感用途说明
        is_enabled, -- 系统身份默认可用；机构接口仍须单独完成自动校验
        created_by, -- 系统初始化操作人
        updated_by -- 系统初始化操作人
    )
    VALUES (
        N'PRIMARY_HIS',
        N'基层HIS',
        N'基层医疗机构HIS接口；按机构维护地址和接入信息，用于基础数据同步。',
        1,
        N'platform-database-init',
        N'platform-database-init'
    );
END;
