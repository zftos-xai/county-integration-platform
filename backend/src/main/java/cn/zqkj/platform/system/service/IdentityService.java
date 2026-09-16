package cn.zqkj.platform.system.service;

import cn.zqkj.platform.system.domain.dto.BootstrapCommand;
import cn.zqkj.platform.system.domain.vo.BootstrapResultVO;
import cn.zqkj.platform.system.domain.vo.BootstrapStatusVO;

/**
 * 定义平台初始化、身份验证辅助和密码管理服务。
 */
public interface IdentityService {

    /** @return 当前安全引导状态 */
    BootstrapStatusVO getBootstrapStatus();

    /**
     * @param suppliedSecret 部署环境提供的安全引导密钥
     * @param command 初始机构和管理员命令
     * @return 初始化结果
     */
    BootstrapResultVO bootstrap(String suppliedSecret, BootstrapCommand command);

    /**
     * @param userId 当前用户主键
     * @param currentPassword 当前密码
     * @param newPassword 新密码
     */
    void changePassword(long userId, String currentPassword, String newPassword);
}
