package cn.zqkj.platform.framework.config;

import cn.zqkj.platform.framework.security.filter.ExternalSystemApiKeyAuthenticationFilter;
import cn.zqkj.platform.system.configuration.service.ExternalSystemInboundKeyAuthenticator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/** 为县医院等外部系统提供独立于管理会话的ID/Key认证安全链。 */
@Configuration
public class IntegrationSecurityConfiguration {

    /**
     * 建立只接受外部系统ID/Key的无状态业务入口安全链。
     *
     * @param http Spring Security配置入口
     * @param apiKeyFilter 外部系统ID/Key认证过滤器
     * @return 独立于管理端会话与CSRF的集成入口安全链
     * @throws Exception 安全过滤链无法构建时抛出
     */
    @Bean
    @Order(1)
    SecurityFilterChain integrationSecurityFilterChain(
            HttpSecurity http,
            ExternalSystemApiKeyAuthenticationFilter apiKeyFilter
    ) throws Exception {
        return http
                .securityMatcher("/api/integration/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .addFilterBefore(apiKeyFilter, AnonymousAuthenticationFilter.class)
                .build();
    }

    /**
     * 创建县医院调用入口认证过滤器。
     *
     * @param authenticator 外部系统ID/Key认证服务
     * @param objectMapper 统一JSON响应序列化器
     * @return 入站身份认证过滤器
     */
    @Bean
    ExternalSystemApiKeyAuthenticationFilter externalSystemApiKeyAuthenticationFilter(
            ExternalSystemInboundKeyAuthenticator authenticator,
            ObjectMapper objectMapper
    ) {
        return new ExternalSystemApiKeyAuthenticationFilter(authenticator, objectMapper);
    }

    /**
     * 阻止Key过滤器被Servlet容器全局注册，确保只在集成安全链内校验请求头。
     *
     * @param filter 集成安全链使用的Key过滤器
     * @return 已禁用容器级自动注册的过滤器注册描述
     */
    @Bean
    FilterRegistrationBean<ExternalSystemApiKeyAuthenticationFilter> disableGlobalApiKeyFilterRegistration(
            ExternalSystemApiKeyAuthenticationFilter filter
    ) {
        FilterRegistrationBean<ExternalSystemApiKeyAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
