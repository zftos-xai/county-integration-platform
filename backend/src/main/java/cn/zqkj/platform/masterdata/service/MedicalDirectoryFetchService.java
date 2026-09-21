package cn.zqkj.platform.masterdata.service;

import cn.zqkj.platform.his.domain.model.MedicalDirectoryType;
import cn.zqkj.platform.masterdata.domain.model.MedicalDirectoryFetchResult;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;

import java.time.LocalDateTime;

/** 取得一类100-004医院目录的全部来源页，并在写库前执行自动校验。 */
public interface MedicalDirectoryFetchService {

    /**
     * 先取得100-005同范围声明行数，再按无重叠分页取得100-004，最后对同一批内存记录校验。
     *
     * <p>任一页返回失败或通信结果未知时立即停止，不自动重试，也不输出部分数据。</p>
     *
     * @param organizationId 平台机构主键
     * @param environment 调用的已配置环境
     * @param directoryType 医院目录类型
     * @param rangeStart 与来源约定一致的开始时间
     * @param rangeEnd 与来源约定一致的结束时间
     * @param sourceOrganizationCode 本次HIS调用使用的机构编码
     * @return 不可变的完整取得与校验结果
     */
    MedicalDirectoryFetchResult fetchAll(
            long organizationId,
            ParameterEnvironment environment,
            MedicalDirectoryType directoryType,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            String sourceOrganizationCode
    );
}
