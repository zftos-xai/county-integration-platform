package cn.zqkj.platform.masterdata.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 接收一次需要并发保护的批次推进请求。
 *
 * @param version 客户端最近读取的批次数据库版本编码
 */
public record AdvanceMasterDataBatchRequest(@NotBlank String version) {
}
