package cn.zqkj.platform.system.configuration.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.validation.ServiceEndpointUrl;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;

/**
 * 更新外部系统服务端点的API输入。
 *
 * <p>写入目标：{@code dbo.sys_external_endpoint}。业务说明：按版本维护连接配置并清除旧校验结果；提交的认证变更经合并、加密写入 {@code dbo.sys_external_endpoint_credential}，此输入不是完整表行。</p>
 *
 * @param environment 环境
 * @param organizationId 可选机构
 * @param baseUrl HTTP或HTTPS地址
 * @param connectTimeoutMs 连接超时
 * @param readTimeoutMs 读取超时
 * @param authentication 可选新认证信息；空值表示保留现有配置
 * @param enabled 兼容旧客户端的字段；保存后始终停用，验证通过才启用
 * @param expectedVersion 八字节行版本，JSON字段version使用Base64
 */
public record UpdateExternalEndpointRequest(@NotNull ParameterEnvironment environment,
                                            @Positive Long organizationId,
                                            @NotBlank @Size(max = 500) @ServiceEndpointUrl String baseUrl,
                                            @Min(100) @Max(60000) int connectTimeoutMs,
                                            @Min(100) @Max(300000) int readTimeoutMs,
                                            @Valid ExternalEndpointAuthenticationCommand authentication,
                                            @NotNull Boolean enabled,
                                            @JsonProperty("version")
                                            @NotNull @Size(min = 8, max = 8) byte[] expectedVersion) {
    /**
     * 裁剪地址文本；URL规则由入口校验，启用状态由自动校验结果决定。
     *
     * @param environment 环境
     * @param organizationId 可选机构
     * @param baseUrl HTTP或HTTPS地址
     * @param connectTimeoutMs 连接超时
     * @param readTimeoutMs 读取超时
     * @param authentication 可选新认证信息；空值表示保留现有配置
     * @param enabled 兼容旧客户端的字段；保存后始终停用，验证通过才启用
     * @param expectedVersion 八字节行版本，JSON字段version使用Base64
     */
    public UpdateExternalEndpointRequest {
        baseUrl = Func.trimToNull(baseUrl);
    }

    /**
     * 返回用于持久化的标准 URI 文本，保留受支持的 ASMX 操作参数。
     * @return 已通过入口校验的地址，路径点段已归一化
     */
    public String normalizedBaseUrl() {
        return URI.create(baseUrl).normalize().toASCIIString();
    }

    /**
     * 保证读取等待时间覆盖连接建立时间。
     * @return 读取超时不短于连接超时时为true
     */
    @AssertTrue(message = "读取超时不能小于连接超时")
    public boolean isReadTimeoutValid() {
        return readTimeoutMs >= connectTimeoutMs;
    }
}
