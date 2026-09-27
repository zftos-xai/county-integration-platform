package cn.zqkj.platform.integration.lis.domain.vo;

import cn.zqkj.platform.his.domain.lis.model.LisItemEntry;
import java.util.List;

/**
 * 返回一次600-001平台操作编号、HIS交换编号和项目包查询结果。
 *
 * @param operationId 平台业务操作编号
 * @param exchangeRequestId 本次基层HIS出站交换编号
 * @param outcome 平台识别的目标结果状态
 * @param targetResultCode 基层HIS明确返回码；结果未知时为空
 * @param message 不含目标原始正文的受控说明
 * @param items 查询成功时返回的项目包列表
 */
public record LisItemPackageQueryResultVO(
        String operationId,
        String exchangeRequestId,
        String outcome,
        String targetResultCode,
        String message,
        List<LisItemEntry> items
) {
}
