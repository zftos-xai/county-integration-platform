package cn.zqkj.platform.masterdata.mapper;

import cn.zqkj.platform.masterdata.domain.dto.MasterDataBatchQuery;
import cn.zqkj.platform.masterdata.domain.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.model.HospitalDirectorySyncResult;
import cn.zqkj.platform.masterdata.domain.model.MasterDataCategory;
import cn.zqkj.platform.masterdata.domain.model.MasterDataScopeType;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 使用MyBatis读写基础数据同步批次。
 */
@Mapper
public interface MasterDataBatchMapper {

    /**
     * 按平台机构代码读取已启用机构主键。
     *
     * @param organizationCode 平台机构代码
     * @return 已启用机构主键；不存在时为空
     */
    Long findEnabledOrganizationId(String organizationCode);

    /**
     * 创建尚未开始处理的同步批次并回填主键。
     *
     * @param batchNo 批次号
     * @param requestKey 请求标识
     * @param sourceSystemCode 来源系统代码
     * @param organizationId 可选平台机构主键
     * @param environment 来源接口运行环境
     * @param category 数据类别
     * @param scopeType 业务归属范围
     * @param dataTradeCode 数据交易码
     * @param countTradeCode 可选数量交易码
     * @param sourceType 可选来源目录类型
     * @param sourceOrganizationId 机构范围批次的已验证来源机构标识
     * @param rangeStart 可选查询范围开始UTC时间
     * @param rangeEnd 可选查询范围结束UTC时间
     * @param diagnosisCategory 可选诊断疾病类别
     * @param diagnosisVersion 可选诊断版本
     * @param actor 创建人标识
     * @return 新建批次主键
     */
    long create(
            @Param("batchNo") String batchNo,
            @Param("requestKey") String requestKey,
            @Param("sourceSystemCode") String sourceSystemCode,
            @Param("organizationId") Long organizationId,
            @Param("environment") ParameterEnvironment environment,
            @Param("category") MasterDataCategory category,
            @Param("scopeType") MasterDataScopeType scopeType,
            @Param("dataTradeCode") String dataTradeCode,
            @Param("countTradeCode") String countTradeCode,
            @Param("sourceType") String sourceType,
            @Param("sourceOrganizationId") String sourceOrganizationId,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd,
            @Param("diagnosisCategory") String diagnosisCategory,
            @Param("diagnosisVersion") String diagnosisVersion,
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
     * 读取一个100-003批次按目录类型保存的运行事实。
     *
     * @param batchId 同步批次主键
     * @return 按HIS目录类型编码升序的分项结果；历史批次可能为空
     */
    List<HospitalDirectorySyncResult> findDirectoryResults(@Param("batchId") long batchId);

    /**
     * 取消尚未执行的批次并释放同范围活动批次占位。
     *
     * @param id 批次主键
     * @param expectedVersion 调用方最近读取的并发版本
     * @param actor 操作人标识
     * @return 实际更新行数
     */
    int cancel(
            @Param("id") long id,
            @Param("expectedVersion") byte[] expectedVersion,
            @Param("actor") String actor
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
