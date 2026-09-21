package cn.zqkj.platform.framework.config;

import cn.zqkj.platform.system.mapper.IdentityMapper;
import cn.zqkj.platform.framework.security.filter.ActiveAccountFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * 配置平台本地管理身份的服务端会话、CSRF和默认拒绝边界。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    /**
     * 创建有状态管理会话安全过滤链。
     *
     * @param http Spring Security 配置入口
     * @param accountFilter 已认证账号状态复核过滤器
     * @return 已构建的安全过滤链
     * @throws Exception 安全过滤链无法构建时抛出
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ActiveAccountFilter accountFilter) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        return http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health", "/actuator/info",
                                "/api/v1/bootstrap/status", "/api/v1/bootstrap",
                                "/api/v1/session/csrf", "/api/v1/session/login"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterAfter(accountFilter, SecurityContextHolderFilter.class)
                .build();
    }

    /**
     * 创建BCrypt强密码哈希器。
     *
     * @return BCrypt密码哈希器
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * 创建本地认证管理器。
     *
     * @param userDetailsService 平台用户查询服务
     * @param passwordEncoder 密码哈希器
     * @return 平台认证管理器
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * 配置仅从服务端会话读取和保存安全上下文的仓储。
     *
     * @return 显式保存到HTTP会话的登录信息存储
     */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * 创建每次请求重新核对账号可用状态的安全过滤器。
     *
     * @param repository 身份持久化边界
     * @return 已认证账号状态复核过滤器
     */
    @Bean
    ActiveAccountFilter activeAccountFilter(IdentityMapper repository) {
        return new ActiveAccountFilter(repository);
    }
}
