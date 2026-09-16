package cn.zqkj.platform.foundation.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * 配置平台 HTTP 安全边界和默认拒绝的身份入口。
 *
 * <p>正式医院身份集成获批前不提供本地用户。</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    /**
     * 创建无状态 HTTP 安全过滤链。
     *
     * @param http Spring Security 配置入口
     * @return 已构建的安全过滤链
     * @throws Exception 安全过滤链无法构建时抛出
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    /**
     * 创建默认拒绝所有本地账号的用户查询服务。
     *
     * @return 不接受任何本地用户名的用户查询服务
     */
    @Bean
    UserDetailsService userDetailsService() {
        // No local user is accepted until the production identity integration is approved.
        return username -> {
            throw new UsernameNotFoundException("Local users are disabled");
        };
    }
}
