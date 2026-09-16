package cn.zqkj.platform.system.service;

import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * 标识从平台身份库装载Spring Security主体的服务边界。
 */
public interface PlatformUserDetailsService extends UserDetailsService {
}
