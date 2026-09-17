package cn.zqkj.platform.system.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.system.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.model.ManagementAuditEvent;
import cn.zqkj.platform.system.domain.vo.ManagementAuditEventVO;
import cn.zqkj.platform.system.mapper.ManagementAuditMapper;
import cn.zqkj.platform.system.service.ManagementAuditService;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 实现只追加、脱敏和机构范围过滤的管理审计规则。 */
@Service
public class ManagementAuditServiceImpl implements ManagementAuditService {

    private final ManagementAuditMapper mapper;

    /** @param mapper 审计Mapper */
    public ManagementAuditServiceImpl(ManagementAuditMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public long recordSuccess(ManagementAuditCommand command) {
        validate(command, "SUCCESS");
        return mapper.insert(command);
    }

    /** {@inheritDoc} */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void recordLoginFailure(String actorHint, String requestId) {
        mapper.insert(new ManagementAuditCommand(null, maskActorHint(actorHint), null, null,
                "LOGIN_FAILED", "SESSION", "LOCAL_LOGIN", "FAILURE",
                "本地登录校验失败；未记录密码或其他凭证", requestId));
    }

    /** {@inheritDoc} */
    @Transactional(readOnly = true)
    @Override
    public List<ManagementAuditEventVO> findVisible(
            AccessActor actor,
            String targetType,
            String targetId,
            int limit
    ) {
        if (limit < 1 || limit > 200) {
            throw new InvalidRequestException("limit must be between 1 and 200");
        }
        return mapper.findVisible(actor.organizationCodes(), normalizeFilter(targetType),
                        normalizeFilter(targetId), limit).stream().map(this::toVO).toList();
    }

    /** @param command 命令 @param expectedResult 预期结果 */
    private void validate(ManagementAuditCommand command, String expectedResult) {
        if (command == null || command.actor() == null || !expectedResult.equals(command.resultCode())) {
            throw new InvalidRequestException("Trusted audit actor and result are required");
        }
        requireText(command.actionCode(), "actionCode", 64);
        requireText(command.targetType(), "targetType", 64);
        requireText(command.targetId(), "targetId", 128);
        requireText(command.changeSummary(), "changeSummary", 1000);
    }

    /** @param value 可选筛选值 @return 裁剪值 */
    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** @param value 文本 @param field 字段 @param max 最大长度 */
    private void requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new InvalidRequestException(field + " is invalid");
        }
    }

    /** @param value 登录名输入 @return 不可还原的短提示 */
    private String maskActorHint(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim();
        return normalized.length() == 1 ? "*" : normalized.charAt(0) + "***";
    }

    /** @param event 事件 @return API输出 */
    private ManagementAuditEventVO toVO(ManagementAuditEvent event) {
        return new ManagementAuditEventVO(event.id(), event.occurredAt(), event.actorLoginSnapshot(),
                event.organizationCodeSnapshot(), event.actionCode(), event.targetType(), event.targetId(),
                event.resultCode(), event.changeSummary(), event.requestId());
    }

    /** @return 当前请求编号 */
    public static String currentRequestId() {
        return MDC.get("requestId");
    }
}
