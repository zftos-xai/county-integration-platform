package cn.zqkj.platform.masterdata.domain.batch.vo;

import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;

import java.util.List;

/**
 * 表示当前操作人可以发起基础数据同步的真实HIS来源。
 *
 * @param organizationCode 机构代码
 * @param organizationName 机构名称
 * @param environments 已启用且认证信息可解析的运行环境
 */
public record MasterDataSyncSourceVO(
        String organizationCode,
        String organizationName,
        List<ParameterEnvironment> environments
) {
    /**
     * 防止调用方持有可变环境集合。
     *
     * @param organizationCode 机构代码
     * @param organizationName 机构名称
     * @param environments 已启用且认证信息可解析的运行环境
     */
    public MasterDataSyncSourceVO {
        environments = List.copyOf(environments);
    }
}
