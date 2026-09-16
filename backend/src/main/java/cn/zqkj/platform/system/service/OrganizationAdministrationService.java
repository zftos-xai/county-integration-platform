package cn.zqkj.platform.system.service;

import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.domain.dto.UpdateOrganizationCommand;

import java.util.List;

/**
 * 定义带机构数据范围约束的机构管理服务。
 */
public interface OrganizationAdministrationService {

    /** @param enabled 可选启用状态 @param actor 操作人 @return 范围内机构 */
    List<OrganizationVO> findAll(Boolean enabled, AccessActor actor);

    /** @param organizationId 机构主键 @param actor 操作人 @return 范围内机构 */
    OrganizationVO get(long organizationId, AccessActor actor);

    /** @param command 创建命令 @param actor 操作人 @return 新机构 */
    OrganizationVO create(CreateOrganizationCommand command, AccessActor actor);

    /** @param organizationId 机构主键 @param command 修改命令 @param actor 操作人 @return 修改后机构 */
    OrganizationVO update(long organizationId, UpdateOrganizationCommand command, AccessActor actor);

    /**
     * @param organizationId 机构主键
     * @param enabled 目标状态
     * @param expectedVersion 并发版本
     * @param actor 操作人
     * @return 修改后机构
     */
    OrganizationVO setEnabled(long organizationId, boolean enabled, byte[] expectedVersion, AccessActor actor);
}
