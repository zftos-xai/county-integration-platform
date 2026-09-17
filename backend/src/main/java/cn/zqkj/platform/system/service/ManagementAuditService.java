package cn.zqkj.platform.system.service;

import cn.zqkj.platform.system.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.vo.ManagementAuditEventVO;

import java.util.List;

/** 管理审计追加与授权查询边界。 */
public interface ManagementAuditService {

    /** @param command 成功事件命令 @return 新事件主键 */
    long recordSuccess(ManagementAuditCommand command);

    /** @param actorHint 脱敏主体提示 @param requestId 请求编号 */
    void recordLoginFailure(String actorHint, String requestId);

    /** @param actor 查询主体 @param targetType 可选类型 @param targetId 可选标识 @param limit 最大条数 @return 可见事件 */
    List<ManagementAuditEventVO> findVisible(AccessActor actor, String targetType, String targetId, int limit);
}
