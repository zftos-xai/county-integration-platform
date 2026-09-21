package cn.zqkj.platform.system.organization.service;

import cn.zqkj.platform.system.organization.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.organization.domain.dto.UpdateOrganizationCommand;

import java.util.List;

/**
 * 定义平台机构档案、层级和启停服务。
 */
public interface OrganizationService {

    /**
     * 创建机构档案，并校验层级、编码和有效期规则。
     *
     * @param command 创建命令
     * @param actor 操作人
     * @return 新机构
     */
    OrganizationVO create(CreateOrganizationCommand command, String actor);

    /**
     * 按主键读取机构档案；不存在时由调用边界按约定处理。
     *
     * @param id 机构主键
     * @return 机构记录
     */
    OrganizationVO get(long id);

    /**
     * 查询当前调用方可见的机构档案列表。
     *
     * @param enabled 可选启用状态
     * @return 机构列表
     */
    List<OrganizationVO> findAll(Boolean enabled);

    /**
     * 按并发版本更新机构档案并返回最新视图。
     *
     * @param id 机构主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改后机构
     */
    OrganizationVO update(long id, UpdateOrganizationCommand command, String actor);

    /**
     * 按并发版本修改机构档案的启用状态。
     *
     * @param id 机构主键
     * @param enabled 目标启用状态
     * @param expectedVersion 并发版本
     * @param actor 操作人
     * @return 修改后机构
     */
    OrganizationVO setEnabled(long id, boolean enabled, byte[] expectedVersion, String actor);
}
