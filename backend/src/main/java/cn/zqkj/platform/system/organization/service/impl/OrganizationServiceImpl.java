package cn.zqkj.platform.system.organization.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.common.exception.ResourceNotFoundException;
import cn.zqkj.platform.system.organization.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.organization.domain.dto.UpdateOrganizationCommand;
import cn.zqkj.platform.system.organization.mapper.OrganizationMapper;
import cn.zqkj.platform.system.organization.service.OrganizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 实现平台机构创建、查询、层级、并发修改和启停规则。
 */
@Service
public class OrganizationServiceImpl implements OrganizationService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");
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
        requireActor(actor);
        validateCode(command.organizationCode());
        validateText(command.organizationName(), "organizationName", 200);
        validateText(command.organizationType(), "organizationType", 32);
        validateTimeRange(command.validFrom(), command.validTo());
        if (mapper.countByCode(command.organizationCode().trim()) > 0) {
            throw new ResourceConflictException("机构编码已存在");
        }
        validateParentChain(-1L, command.parentId());
        CreateOrganizationCommand normalized = new CreateOrganizationCommand(
                command.organizationCode().trim(),
                command.organizationName().trim(),
                command.organizationType().trim(),
                command.parentId(),
                command.validFrom(),
                command.validTo()
        );
        long organizationId = mapper.create(normalized, actor.trim());
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
        requirePositiveId(id);
        return requireOrganization(id);
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
        requirePositiveId(id);
        requireActor(actor);
        validateText(command.organizationName(), "organizationName", 200);
        validateText(command.organizationType(), "organizationType", 32);
        validateTimeRange(command.validFrom(), command.validTo());
        requireVersion(command.expectedVersion());
        get(id);
        validateParentChain(id, command.parentId());
        UpdateOrganizationCommand normalized = new UpdateOrganizationCommand(
                command.organizationName().trim(),
                command.organizationType().trim(),
                command.parentId(),
                command.validFrom(),
                command.validTo(),
                command.expectedVersion()
        );
        if (mapper.update(id, normalized, actor.trim()) != 1) {
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
        requirePositiveId(id);
        requireActor(actor);
        requireVersion(expectedVersion);
        get(id);
        if (!enabled && mapper.countEnabledChildren(id) > 0) {
            throw new ResourceConflictException("该机构仍有正常使用的下级机构，不能撤销；请先逐个撤销下级机构");
        }
        if (mapper.setEnabled(id, enabled, expectedVersion, actor.trim()) != 1) {
            throw new ResourceConflictException("机构资料已被他人修改，请刷新后重试");
        }
        return get(id);
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

    /**
     * 校验机构编码只包含受控字符。
     *
     * @param code 机构编码
     */
    private void validateCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code.trim()).matches()) {
            throw new InvalidRequestException("organizationCode 格式无效");
        }
    }

    /**
     * 校验必填文本的空白和长度边界。
     *
     * @param value 文本值
     * @param field 字段名
     * @param maximumLength 最大长度
     */
    private void validateText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.trim().length() > maximumLength) {
            throw new InvalidRequestException(field + " is invalid");
        }
    }

    /**
     * 校验有效结束不早于起始。
     *
     * @param validFrom 可选起始时间
     * @param validTo 可选结束时间
     */
    private void validateTimeRange(LocalDateTime validFrom, LocalDateTime validTo) {
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new InvalidRequestException("validTo 不能早于 validFrom");
        }
    }

    /**
     * 校验主键为正数。
     *
     * @param id 资源主键
     */
    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw new InvalidRequestException("id 必须大于 0");
        }
    }

    /**
     * 校验操作人标识存在且长度受控。
     *
     * @param actor 操作人标识
     */
    private void requireActor(String actor) {
        validateText(actor, "actor", 64);
    }

    /**
     * 校验客户端提供了SQL Server并发版本。
     *
     * @param version 并发版本二进制值
     */
    private void requireVersion(byte[] version) {
        if (version == null || version.length != Long.BYTES) {
            throw new InvalidRequestException("version 必须是 8 字节的 SQL Server 行版本号");
        }
    }
}
