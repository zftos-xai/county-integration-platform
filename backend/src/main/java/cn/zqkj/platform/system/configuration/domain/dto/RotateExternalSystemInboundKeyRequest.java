package cn.zqkj.platform.system.configuration.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 重置外部系统入站Key的并发版本输入。
 *
 * @param expectedVersion 当前外部系统八字节行版本；JSON字段version使用Base64
 */
public record RotateExternalSystemInboundKeyRequest(
        @JsonProperty("version") @NotNull @Size(min = 8, max = 8) byte[] expectedVersion
) {
    /**
     * 防止调用方修改行版本数组，保持并发条件稳定。
     *
     * @param expectedVersion 当前外部系统八字节行版本
     */
    public RotateExternalSystemInboundKeyRequest {
        expectedVersion = expectedVersion == null ? null : expectedVersion.clone();
    }
}
