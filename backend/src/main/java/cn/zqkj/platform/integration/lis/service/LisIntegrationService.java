package cn.zqkj.platform.integration.lis.service;

import cn.zqkj.platform.integration.lis.domain.dto.LisItemPackageQueryRequest;
import cn.zqkj.platform.integration.lis.domain.vo.LisItemPackageQueryResultVO;
import cn.zqkj.platform.system.configuration.domain.model.ExternalSystemCaller;

/** 编排县医院入站调用、平台机构路由、基层HIS查询和最小交换留痕。 */
public interface LisIntegrationService {

    /**
     * 通过600-001查询指定平台机构的检验项目包，并记录实际基层HIS交换结果。
     *
     * @param caller 已通过外部系统ID/Key认证的调用方
     * @param request 平台机构代码和项目包类型
     * @param operationId 由HTTP入口生成的平台操作编号
     * @return 操作编号、实际出站交换编号及查询结果
     */
    LisItemPackageQueryResultVO queryItemPackages(
            ExternalSystemCaller caller,
            LisItemPackageQueryRequest request,
            String operationId
    );
}
