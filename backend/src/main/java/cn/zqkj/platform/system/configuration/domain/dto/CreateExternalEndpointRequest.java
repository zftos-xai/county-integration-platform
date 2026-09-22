package cn.zqkj.platform.system.configuration.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.validation.ServiceEndpointUrl;
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
 * 创建外部系统服务端点的API输入。
 *
 * <p>写入目标：{@code dbo.sys_external_endpoint}。业务说明：登记系统在环境和机构下的连接配置；认证字段经加密写入 {@code dbo.sys_external_endpoint_credential}，初始禁用，此输入不是完整表行。</p>
 *
 * @param environment 环境
 * @param organizationId 可选机构
 * @param baseUrl HTTP或HTTPS地址
 * @param connectTimeoutMs 连接超时
 * @param readTimeoutMs 读取超时
 * @param authentication 接口认证信息
 * @param enabled 兼容旧客户端的字段；保存后始终停用，验证通过才启用
 */
public record CreateExternalEndpointRequest(@NotNull ParameterEnvironment environment, @Positive Long organizationId,
                                            @NotBlank @Size(max = 500) @ServiceEndpointUrl String baseUrl,
                                            @Min(100) @Max(60000) int connectTimeoutMs,
                                            @Min(100) @Max(300000) int readTimeoutMs,
                                            @Valid @NotNull ExternalEndpointAuthenticationCommand authentication,
                                            @NotNull Boolean enabled) {
    /**
     * 裁剪地址文本；URL规则由入口校验，启用状态由自动校验结果决定。
     *
     * @param environment 环境
     * @param organizationId 可选机构
     * @param baseUrl HTTP或HTTPS地址
     * @param connectTimeoutMs 连接超时
     * @param readTimeoutMs 读取超时
     * @param authentication 接口认证信息
     * @param enabled 兼容旧客户端的字段；保存后始终停用，验证通过才启用
     */
    public CreateExternalEndpointRequest {
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
     * 创建时要求厂商、授权码齐全，用户名和密码成对提交。
     * @return 初次认证信息完整时为 true；空对象由 NotNull 处理
     */
    @AssertTrue(message = "首次配置必须提供厂商和授权码；用户名与密码须同时填写")
    public boolean isAuthenticationComplete() {
        return authentication == null || (Func.isNotBlank(authentication.vendorCode())
                && Func.isNotBlank(authentication.authorizationCode())
                && Func.isBlank(authentication.username()) == Func.isBlank(authentication.password()));
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
