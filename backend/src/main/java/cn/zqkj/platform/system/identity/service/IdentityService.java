package cn.zqkj.platform.system.identity.service;

import cn.zqkj.platform.system.bootstrap.domain.dto.BootstrapCommand;
import cn.zqkj.platform.system.bootstrap.domain.vo.BootstrapResultVO;
import cn.zqkj.platform.system.bootstrap.domain.vo.BootstrapStatusVO;

/**
 * 定义平台初始化、身份验证辅助和密码管理服务。
 */
public interface IdentityService {

    /**
     * 查询平台是否仍允许执行一次性安全引导。
     *
     * @return 当前安全引导状态
     */
    BootstrapStatusVO getBootstrapStatus();

    /**
     * 执行一次性安全引导并创建初始机构、管理员和授权关系。
     *
     * @param suppliedSecret 部署环境提供的安全引导密钥
     * @param command 初始机构和管理员命令
     * @return 初始化结果
     */
    BootstrapResultVO bootstrap(String suppliedSecret, BootstrapCommand command);

    /**
     * 校验旧密码后修改当前用户密码并解除强制改密状态。
     *
     * @param userId 当前用户主键
     * @param currentPassword 当前密码
     * @param newPassword 新密码
     */
    void changePassword(long userId, String currentPassword, String newPassword);
}
