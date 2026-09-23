-- 业务域: exchange
-- 脚本类型: PATCH
-- 归属主版本: V0100
-- 补丁序号: 002
-- 变更说明: 注册交换记录查询权限并授予已有平台管理员角色
-- 需求依据: 交换记录管理页受exchange:read保护，平台管理员必须能够读取平台交换记录
-- 数据边界: 仅增加平台管理员角色的查询权限，不扩大其他角色或机构数据范围
-- 时间规则: 不新增或修改时间字段
-- 回退方案: 删除本补丁授权的PLATFORM_ADMIN权限关系；仅当权限代码未被任何角色引用时删除注册项

IF NOT EXISTS (SELECT 1 FROM dbo.sys_permission WHERE permission_code = N'exchange:read')
    INSERT INTO dbo.sys_permission (permission_code, permission_name)
    VALUES (N'exchange:read', N'查询交换记录');

INSERT INTO dbo.sys_role_permission (role_id, permission_code, granted_by)
SELECT role_row.id, permission_row.permission_code, N'migration-V1300'
FROM dbo.sys_role AS role_row
INNER JOIN dbo.sys_permission AS permission_row
    ON permission_row.permission_code = N'exchange:read'
WHERE role_row.role_code = N'PLATFORM_ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM dbo.sys_role_permission AS existing_permission
      WHERE existing_permission.role_id = role_row.id
        AND existing_permission.permission_code = permission_row.permission_code
  );
