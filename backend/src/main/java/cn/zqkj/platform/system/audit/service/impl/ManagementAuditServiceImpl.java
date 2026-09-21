package cn.zqkj.platform.system.audit.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditQuery;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.audit.domain.model.ManagementAuditEvent;
import cn.zqkj.platform.system.audit.domain.vo.ManagementAuditEventVO;
import cn.zqkj.platform.system.audit.mapper.ManagementAuditMapper;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 实现只追加、脱敏和机构范围过滤的管理审计规则。 */
@Service
public class ManagementAuditServiceImpl implements ManagementAuditService {

    private final ManagementAuditMapper mapper;

    /**
     * 创建管理审计服务。
     *
     * @param mapper 审计Mapper
     */
    public ManagementAuditServiceImpl(ManagementAuditMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>必须加入调用方事务，使业务变更与成功审计同成同败，避免留下虚假成功记录。</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public long recordSuccess(ManagementAuditCommand command) {
        validate(command, "SUCCESS");
        return mapper.insert(command);
    }

    /**
     * {@inheritDoc}
     *
     * <p>使用独立事务记录失败事件，并只保存脱敏后的账号提示，不保存密码、令牌或凭证。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void recordLoginFailure(String actorHint, String requestId) {
        mapper.insert(new ManagementAuditCommand(null, maskActorHint(actorHint), null, null,
                "LOGIN_FAILED", "SESSION", "LOCAL_LOGIN", "FAILURE",
                "本地登录校验失败；未记录密码或其他凭证", requestId));
    }

    /**
     * {@inheritDoc}
     *
     * <p>规范化筛选条件并按机构范围查询，使用时间与主键组成稳定的历史游标。</p>
     */
    @Transactional(readOnly = true)
    @Override
    public List<ManagementAuditEventVO> findVisible(
            AccessActor actor,
            ManagementAuditQuery query
    ) {
        if (query == null || query.limit() < 1 || query.limit() > 200) {
            throw new InvalidRequestException("limit 必须在 1 到 200 之间");
        }
        if (query.occurredFrom() != null && query.occurredTo() != null
                && query.occurredFrom().isAfter(query.occurredTo())) {
            throw new InvalidRequestException("开始时间不能晚于结束时间");
        }
        if ((query.beforeOccurredAt() == null) != (query.beforeId() == null)) {
            throw new InvalidRequestException("继续查询历史记录时必须同时提供时间和记录编号");
        }
        ManagementAuditQuery normalized = new ManagementAuditQuery(normalizeFilter(query.actorLogin()),
                normalizeFilter(query.actionCode()), normalizeFilter(query.targetType()),
                normalizeFilter(query.targetId()), normalizeFilter(query.requestId()),
                normalizeResult(query.resultCode()),
                query.occurredFrom(), query.occurredTo(), query.beforeOccurredAt(), query.beforeId(),
                query.limit());
        return mapper.findVisible(actor.organizationCodes(), normalized).stream().map(this::toVO).toList();
    }

    /**
     * 校验输入对象的必填字段、长度和安全边界。
     *
     * @param command 命令
     * @param expectedResult 预期结果
     */
    private void validate(ManagementAuditCommand command, String expectedResult) {
        if (command == null || command.actor() == null || !expectedResult.equals(command.resultCode())) {
            throw new InvalidRequestException("写入审计记录时必须提供可信的操作人和操作结果");
        }
        requireText(command.actionCode(), "actionCode", 64);
        requireText(command.targetType(), "targetType", 64);
        requireText(command.targetId(), "targetId", 128);
        requireText(command.changeSummary(), "changeSummary", 1000);
    }

    /**
     * 规范化可选审计筛选条件。
     *
     * @param value 可选筛选值
     * @return 裁剪值
     */
    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 规范化并限制审计结果代码。
     *
     * @param value 可选结果代码
     * @return 规范结果代码
     */
    private String normalizeResult(String value) {
        String normalized = normalizeFilter(value);
        if (normalized == null) {
            return null;
        }
        if (!"SUCCESS".equals(normalized) && !"FAILURE".equals(normalized)) {
            throw new InvalidRequestException("处理结果只能是成功或失败");
        }
        return normalized;
    }

    /**
     * 校验审计必填文本并返回裁剪后的值。
     *
     * @param value 文本
     * @param field 字段
     * @param max 最大长度
     */
    private void requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new InvalidRequestException(field + " is invalid");
        }
    }

    /**
     * 将登录失败用户名提示脱敏后写入审计。
     *
     * @param value 登录名输入
     * @return 不可还原的短提示
     */
    private String maskActorHint(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim();
        return normalized.length() == 1 ? "*" : normalized.charAt(0) + "***";
    }

    /**
     * 把内部持久化快照转换为API输出。
     *
     * @param event 事件
     * @return API输出
     */
    private ManagementAuditEventVO toVO(ManagementAuditEvent event) {
        return new ManagementAuditEventVO(event.id(), event.occurredAt(), event.actorLoginSnapshot(),
                event.organizationCodeSnapshot(), event.actionCode(), event.targetType(), event.targetId(),
                event.resultCode(), event.changeSummary(), event.requestId());
    }

    /**
     * 读取当前请求链路编号；没有Web请求时返回空值。
     *
     * @return 当前请求编号
     */
    @Override
    public String currentRequestId() {
        return MDC.get("requestId");
    }
}
