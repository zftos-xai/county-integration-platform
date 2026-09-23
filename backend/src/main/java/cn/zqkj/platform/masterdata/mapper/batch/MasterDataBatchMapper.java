package cn.zqkj.platform.masterdata.mapper.batch;

import cn.zqkj.platform.masterdata.domain.batch.dto.MasterDataBatchQuery;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchCreation;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用MyBatis读写基础数据同步批次。
 */
@Mapper
public interface MasterDataBatchMapper {

    /**
     * 在短事务内锁住指定执行版本，阻止恢复操作与旧执行者同时写入。
     * @param batchId 已确认范围内的批次主键
     * @param expectedVersion 抢占执行后或人工恢复时读取的版本
     * @return 批次仍属于该执行版本且未结束时为true
     */
    boolean lockExecution(@Param("batchId") long batchId, @Param("expectedVersion") byte[] expectedVersion);

    /**
     * 在类型发布事务中锁定绑定端点，防止分页期间配置变化后写入混合来源结果。
     *
     * @param batchId 已取得执行权的批次主键
     * @return 创建时绑定的端点已变更或失效时为true；无历史绑定时为false
     */
    boolean hasBoundSourceChanged(@Param("batchId") long batchId);

    /**
     * 读取创建公共批次时所绑定端点的所属机构。
     *
     * <p>该机构只用于重新解析已选端点的运行时凭证，绝不表示公共目录或批次归属。</p>
     *
     * @param batchId 平台范围ICD10批次主键
     * @return 已绑定端点的启用机构主键；端点不存在或没有机构绑定时为空
     */
    Long findBoundEndpointOrganizationId(@Param("batchId") long batchId);

    /**
     * 按平台机构代码读取已启用机构主键。
     *
     * @param organizationCode 平台机构代码
     * @return 已启用机构主键；不存在时为空
     */
    Long findEnabledOrganizationId(String organizationCode);

    /**
     * 阻止不同HIS环境向同一未按环境隔离的医疗目录表写入数据。
     *
     * @param organizationId 当前机构主键
     * @param environment 即将运行的HIS环境
     * @return 当前目录含其他环境最近写入的记录时为true
     */
    boolean hasMedicalCatalogFromOtherEnvironment(
            @Param("organizationId") long organizationId,
            @Param("environment") cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment environment
    );

    /**
     * 创建待执行批次；请求标识和活动范围唯一约束拒绝重复创建。
     *
     * @param batchNo 新生成的批次号
     * @param sourceSystemCode 来源系统稳定代码
     * @param creation 已解析为固定UTC范围的创建事实
     * @param organizationId 已确认启用的平台机构主键
     * @param sourceOrganizationId 按交易要求解析的来源机构标识
     * @param actor 创建人名称快照
     * @return 新批次主键
     */
    long create(
            @Param("batchNo") String batchNo,
            @Param("sourceSystemCode") String sourceSystemCode,
            @Param("creation") MasterDataBatchCreation creation,
            @Param("organizationId") Long organizationId,
            @Param("sourceOrganizationId") String sourceOrganizationId,
            @Param("actor") String actor
    );

    /**
     * 按主键读取基础数据同步批次；不存在时由调用边界按约定处理。
     *
     * @param id 批次主键
     * @return 批次快照；不存在时为空
     */
    MasterDataBatchSnapshot findById(long id);

    /**
     * 用客户端行版本原子占用可执行批次，阻止重复请求同时调用 HIS。
     *
     * @param batchId 已确定机构范围的批次主键
     * @param expectedVersion 入口已解码的八字节行版本
     * @param actor 执行人名称快照
     * @return 占用成功为 1，状态或版本变化为 0
     */
    int beginFetch(@Param("batchId") long batchId, @Param("expectedVersion") byte[] expectedVersion,
                   @Param("actor") String actor);

    /**
     * 在 SQL 中按机构范围读取批次，避免先取得越权批次再检查归属。
     *
     * @param id 批次主键
     * @param organizationCodes 调用入口确认的机构代码；空集合只能读取平台级批次
     * @return 可见批次快照；不存在或超出范围时为空
     */
    MasterDataBatchSnapshot findVisibleById(
            @Param("id") long id,
            @Param("organizationCodes") List<String> organizationCodes
    );

    /**
     * 取消尚未执行的批次并释放同范围活动批次占位。
     *
     * @param id 批次主键
     * @param expectedVersion 调用方最近读取的并发版本
     * @param actor 操作人标识
     * @param organizationCodes 调用入口确认的机构代码；写入时仍在 SQL 中核对范围
     * @return 实际更新行数
     */
    int cancel(
            @Param("id") long id,
            @Param("expectedVersion") byte[] expectedVersion,
            @Param("actor") String actor,
            @Param("organizationCodes") List<String> organizationCodes
    );

    /**
     * 统计满足条件的总数。
     *
     * @param query 查询条件
     * @param organizationCodes 当前用户机构范围
     * @return 满足条件的总数
     */
    long countPage(
            @Param("query") MasterDataBatchQuery query,
            @Param("organizationCodes") List<String> organizationCodes
    );

    /**
     * 按有界分页条件查询基础数据同步批次。
     *
     * @param query 查询条件
     * @param organizationCodes 当前用户机构范围
     * @return 当前页批次快照
     */
    List<MasterDataBatchSnapshot> findPage(
            @Param("query") MasterDataBatchQuery query,
            @Param("organizationCodes") List<String> organizationCodes
    );
}
