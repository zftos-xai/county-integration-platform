package cn.zqkj.platform.system.audit.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import jakarta.validation.Validator;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditQuery;
import cn.zqkj.platform.system.audit.domain.model.ManagementAuditEvent;
import cn.zqkj.platform.system.audit.domain.vo.ManagementAuditEventVO;
import cn.zqkj.platform.system.audit.mapper.ManagementAuditMapper;
import cn.zqkj.platform.system.audit.service.ManagementAuditService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 实现只追加、脱敏和机构范围过滤的管理审计规则。 */
@Service
public class ManagementAuditServiceImpl implements ManagementAuditService {

    private final ManagementAuditMapper mapper;
    private final Validator validator;

    /**
     * 创建管理审计服务。
     *
     * @param mapper 审计Mapper
     * @param validator 校验内部流程提交的审计事实，不要求登录会话
     */
    public ManagementAuditServiceImpl(ManagementAuditMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    /**
     * 随业务事务追加操作结果，保留真实的成功或失败状态。
     *
     * <p>要求调用方已开启事务，操作结果和审计必须一起提交或回滚。</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public long append(ManagementAuditCommand command) {
        if (command == null || !validator.validate(command).isEmpty()) {
            throw new InvalidRequestException("审计事实缺少必填信息或超过允许范围");
        }
        return mapper.insert(command);
    }

    /**
     * 独立记录登录失败审计，只保存脱敏账号提示。
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
     * 按机构范围和稳定游标读取管理审计记录。
     *
     * <p>按已校验筛选条件和机构范围查询，使用时间与主键组成稳定的历史游标。</p>
     */
    @Transactional(readOnly = true)
    @Override
    public List<ManagementAuditEventVO> findVisible(
            Set<String> organizationCodes,
            ManagementAuditQuery query
    ) {
        List<ManagementAuditEventVO> result = new ArrayList<>();
        for (ManagementAuditEvent event : mapper.findVisible(organizationCodes, query)) {
            result.add(toVO(event));
        }
        return result;
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
