package cn.zqkj.platform.system.organization.service.impl;

import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.exception.ResourceNotFoundException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.system.organization.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.organization.domain.dto.UpdateOrganizationCommand;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.organization.mapper.OrganizationMapper;
import cn.zqkj.platform.system.organization.service.OrganizationService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实现平台机构创建、查询、层级、并发修改和启停规则。
 */
@Service
public class OrganizationServiceImpl implements OrganizationService {

    private static final int MAX_HIERARCHY_DEPTH = 64;

    private final OrganizationMapper mapper;

    /**
     * 创建机构应用服务。
     *
     * @param mapper 机构持久化边界
     */
    public OrganizationServiceImpl(OrganizationMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 创建机构并验证编码、时间和父机构。
     *
     * @param command 创建命令
     * @param actor 操作人稳定标识
     * @return 新建机构记录
     */
    @Transactional
    @Override
    public OrganizationVO create(CreateOrganizationCommand command, String actor) {
        requireHierarchyLock();
        actor = Func.requireText(actor, "actor", 64);
        if (mapper.countByCode(command.organizationCode()) > 0) {
            throw new ResourceConflictException("机构编码已存在");
        }
        validateParentChain(-1L, command.parentId());
        long organizationId = mapper.create(command, actor);
        return requireOrganization(organizationId);
    }

    /**
     * 读取指定机构。
     *
     * @param id 机构主键
     * @return 机构记录
     */
    @Transactional(readOnly = true)
    @Override
    public OrganizationVO get(long id) {
        return requireOrganization(id);
    }

    /**
     * 在查询中限定机构范围，不向调用方区分不存在与范围外机构。
     * @param id 机构主键
     * @param organizationCodes 获准访问的机构代码
     * @return 可见机构
     */
    @Transactional(readOnly = true)
    @Override
    public OrganizationVO getVisible(long id, List<String> organizationCodes) {
        OrganizationVO organization = mapper.findVisibleById(id, organizationCodes);
        if (organization == null) {
            throw new ResourceNotFoundException("机构不存在或不在当前范围内");
        }
        return organization;
    }

    /**
     * 读取可选按启用状态过滤的机构列表。
     *
     * @param enabled 可选启用状态
     * @return 机构列表
     */
    @Transactional(readOnly = true)
    @Override
    public List<OrganizationVO> findAll(Boolean enabled) {
        return mapper.findAll(enabled);
    }

    /** 按获准机构代码读取机构档案；空范围不返回任何机构。 */
    @Transactional(readOnly = true)
    @Override
    public List<OrganizationVO> findVisible(Boolean enabled, List<String> organizationCodes) {
        return mapper.findVisible(enabled, organizationCodes);
    }

    /**
     * 使用并发版本修改机构。
     *
     * @param id 机构主键
     * @param command 修改命令
     * @param actor 操作人稳定标识
     * @return 修改后机构记录
     */
    @Transactional
    @Override
    public OrganizationVO update(long id, UpdateOrganizationCommand command, String actor) {
        requireHierarchyLock();
        actor = Func.requireText(actor, "actor", 64);
        get(id);
        validateParentChain(id, command.parentId());
        if (mapper.update(id, command, actor) != 1) {
            throw new ResourceConflictException("机构资料已被他人修改，请刷新后重试");
        }
        return get(id);
    }

    /**
     * 使用并发版本恢复或撤销机构。
     *
     * @param id 机构主键
     * @param enabled 目标启用状态
     * @param expectedVersion 客户端上次读取的并发版本
     * @param actor 操作人稳定标识
     * @return 修改后机构记录
     */
    @Transactional
    @Override
    public OrganizationVO setEnabled(long id, boolean enabled, byte[] expectedVersion, String actor) {
        requireHierarchyLock();
        actor = Func.requireText(actor, "actor", 64);
        get(id);
        if (!enabled && mapper.countEnabledChildren(id) > 0) {
            throw new ResourceConflictException("该机构仍有正常使用的下级机构，不能撤销；请先逐个撤销下级机构");
        }
        if (mapper.setEnabled(id, enabled, expectedVersion, actor) != 1) {
            throw new ResourceConflictException("机构资料已被他人修改，请刷新后重试");
        }
        return get(id);
    }

    /** 在读取父链前取得事务锁，提交或回滚后由数据库释放。 */
    private void requireHierarchyLock() {
        if (mapper.lockHierarchy() < 0) {
            throw new ResourceConflictException("机构层级正在修改，请稍后重试");
        }
    }

    /**
     * 校验修改后父链不引用自身且不存在循环。
     *
     * @param organizationId 当前机构主键
     * @param parentId 可选父机构主键
     */
    private void validateParentChain(long organizationId, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long currentId = parentId;
        int depth = 0;
        while (currentId != null) {
            if (currentId == organizationId || !visited.add(currentId)) {
                throw new ResourceConflictException("上级机构设置会造成机构相互包含");
            }
            if (++depth > MAX_HIERARCHY_DEPTH) {
                throw new ResourceConflictException("机构层级超过系统允许的最大层数");
            }
            OrganizationVO current = get(currentId);
            if (!current.enabled()) {
                throw new ResourceConflictException("上级机构已停用");
            }
            currentId = current.parentId();
        }
    }

    /**
     * 读取必须存在的机构记录。
     *
     * @param id 机构主键
     * @return 机构记录
     */
    private OrganizationVO requireOrganization(long id) {
        OrganizationVO organization = mapper.findById(id);
        if (organization == null) {
            throw new ResourceNotFoundException("未找到机构");
        }
        return organization;
    }


}
