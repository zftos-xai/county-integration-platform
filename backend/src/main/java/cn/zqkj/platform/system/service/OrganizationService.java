package cn.zqkj.platform.system.service;

import cn.zqkj.platform.system.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.domain.dto.UpdateOrganizationCommand;

import java.util.List;

/**
 * 定义平台机构档案、层级和启停服务。
 */
public interface OrganizationService {

    /** @param command 创建命令 @param actor 操作主体 @return 新机构 */
    OrganizationVO create(CreateOrganizationCommand command, String actor);

    /** @param id 机构主键 @return 机构快照 */
    OrganizationVO get(long id);

    /** @param enabled 可选启用状态 @return 机构列表 */
    List<OrganizationVO> findAll(Boolean enabled);

    /** @param id 机构主键 @param command 修改命令 @param actor 操作主体 @return 修改后机构 */
    OrganizationVO update(long id, UpdateOrganizationCommand command, String actor);

    /**
     * @param id 机构主键
     * @param enabled 目标启用状态
     * @param expectedVersion 并发版本
     * @param actor 操作主体
     * @return 修改后机构
     */
    OrganizationVO setEnabled(long id, boolean enabled, byte[] expectedVersion, String actor);
}
