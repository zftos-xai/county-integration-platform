package cn.zqkj.platform.system.audit.service;

import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditQuery;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import cn.zqkj.platform.system.audit.domain.vo.ManagementAuditEventVO;

import java.util.List;

/** 管理审计追加与授权查询边界。 */
public interface ManagementAuditService {

    /**
     * 追加一条不含敏感内容的管理操作成功事件。
     *
     * @param command 成功事件命令
     * @return 新事件主键
     */
    long recordSuccess(ManagementAuditCommand command);

    /**
     * 追加不包含密码和凭证的登录失败审计事件。
     *
     * @param actorHint 脱敏用户名提示
     * @param requestId 请求编号
     */
    void recordLoginFailure(String actorHint, String requestId);

    /**
     * 返回当前请求的链路编号；非 Web 调用可返回空值。
     *
     * <p>业务域只能通过审计服务获取该值，不能依赖审计实现类的静态方法，
     * 以免跨域穿透到实现层。</p>
     *
     * @return 当前请求链路编号，或空值
     */
    String currentRequestId();

    /**
     * 查询当前操作人机构范围内可见的管理审计事件。
     *
     * @param actor 查询用户
     * @param query 查询条件
     * @return 可见事件
     */
    List<ManagementAuditEventVO> findVisible(AccessActor actor, ManagementAuditQuery query);
}
