package cn.zqkj.platform.system.audit.mapper;

import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditCommand;
import cn.zqkj.platform.system.audit.domain.dto.ManagementAuditQuery;
import cn.zqkj.platform.system.audit.domain.model.ManagementAuditEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/** 追加和查询管理审计事件，不暴露更新或删除方法。 */
@Mapper
public interface ManagementAuditMapper {

    /**
     * 追加一条不可修改的管理审计事件并返回主键。
     *
     * @param command 事件命令
     * @return 新事件主键
     */
    long insert(ManagementAuditCommand command);

    /**
     * 查询当前操作人机构范围内可见的管理审计事件。
     *
     * @param organizationCodes 可访问机构代码
     * @param query 查询条件
     * @return 时间倒序事件
     */
    List<ManagementAuditEvent> findVisible(@Param("organizationCodes") Set<String> organizationCodes,
                                           @Param("query") ManagementAuditQuery query);
}
