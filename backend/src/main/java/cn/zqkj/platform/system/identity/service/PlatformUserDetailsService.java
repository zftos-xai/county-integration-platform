package cn.zqkj.platform.system.identity.service;

import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * 标识从平台身份库装载Spring Security登录用户对象的服务边界。
 */
public interface PlatformUserDetailsService extends UserDetailsService {
}
