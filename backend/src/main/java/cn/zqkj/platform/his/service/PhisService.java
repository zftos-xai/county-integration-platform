package cn.zqkj.platform.his.service;

import cn.zqkj.platform.his.domain.model.PhisResponse;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 按机构配置调用基层HIS的业务入口。
 */
public interface PhisService {

    /** @param organizationId 机构主键 @param environment 部署环境 @return 接口测试结果 */
    PhisResponse testConnection(long organizationId, ParameterEnvironment environment);

    /** @param organizationId 机构主键 @param environment 部署环境 @return 登录验证结果 */
    PhisResponse login(long organizationId, ParameterEnvironment environment);

    /**
     * 调用使用机构授权码的交易。
     *
     * @param organizationId 机构主键
     * @param environment 部署环境
     * @param tradeCode 交易码
     * @param parameters 不含可信授权码的业务参数
     * @return HIS业务响应
     */
    PhisResponse invokeAuthorized(
            long organizationId,
            ParameterEnvironment environment,
            String tradeCode,
            ObjectNode parameters
    );
}
