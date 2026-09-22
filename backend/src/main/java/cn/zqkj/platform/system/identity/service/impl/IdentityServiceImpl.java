package cn.zqkj.platform.system.identity.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.bootstrap.domain.dto.BootstrapCommand;
import cn.zqkj.platform.system.bootstrap.domain.vo.BootstrapResultVO;
import cn.zqkj.platform.system.bootstrap.domain.vo.BootstrapStatusVO;
import cn.zqkj.platform.system.identity.domain.model.UserAccount;
import cn.zqkj.platform.system.identity.domain.vo.CurrentUserVO;
import cn.zqkj.platform.system.identity.mapper.IdentityMapper;
import cn.zqkj.platform.system.identity.service.IdentityService;
import cn.zqkj.platform.system.organization.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.organization.service.OrganizationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实现平台一次性安全引导、初始管理员创建和本地密码修改。
 */
@Service
public class IdentityServiceImpl implements IdentityService {

    private static final String BOOTSTRAP_ACTOR = "platform-bootstrap";
    private static final int MINIMUM_BOOTSTRAP_SECRET_LENGTH = 32;
    private static final Map<String, String> INITIAL_PERMISSIONS = initialPermissions();

    private final IdentityMapper mapper;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapSecret;

    /**
     * 创建身份应用服务。
     *
     * @param mapper 身份持久化边界
     * @param organizationService 机构应用服务
     * @param passwordEncoder 强密码哈希器
     * @param bootstrapSecret 仅由部署环境提供的一次性启动密钥
     */
    public IdentityServiceImpl(
            IdentityMapper mapper,
            OrganizationService organizationService,
            PasswordEncoder passwordEncoder,
            @Value("${platform.security.bootstrap-secret:}") String bootstrapSecret
    ) {
        this.mapper = mapper;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapSecret = bootstrapSecret == null ? "" : bootstrapSecret;
    }

    /**
     * 读取会话用户的角色和主机构名称供界面辨认；实际功能权限仍取自认证主体。
     *
     * @param principal 服务端认证取得的当前登录用户
     * @return 当前账号、角色和主归属机构摘要
     */
    @Transactional(readOnly = true)
    @Override
    public CurrentUserVO currentUser(PlatformUserPrincipal principal) {
        String organizationName = Optional.ofNullable(mapper.findOrganizationName(principal.primaryOrganizationId()))
                .orElseThrow(() -> new IllegalStateException("Current user's primary organization is unavailable"));
        return CurrentUserVO.from(principal, organizationName, mapper.findEnabledRoleNames(principal.userId()));
    }

    /**
     * 查询不包含启动密钥内容的安全引导状态。
     *
     * @return 当前安全引导状态
     */
    @Transactional(readOnly = true)
    @Override
    public BootstrapStatusVO getBootstrapStatus() {
        boolean initialized = mapper.countUsers() > 0;
        return new BootstrapStatusVO(initialized, !initialized && isBootstrapSecretConfigured());
    }

    /**
     * 在平台尚无用户且启动密钥匹配时原子创建首个机构和管理员授权关系。
     *
     * @param suppliedSecret 请求提供的一次性启动密钥
     * @param command 安全引导输入
     * @return 非敏感引导结果
     */
    @Transactional
    @Override
    public BootstrapResultVO bootstrap(String suppliedSecret, BootstrapCommand command) {
        validateBootstrapSecret(suppliedSecret);
        rejectPasswordContainingLogin(command.initialPassword(), command.loginName());
        if (mapper.lockAndCountUsers() > 0) {
            throw new ResourceConflictException("平台初始管理员已经创建，不能重复初始化");
        }

        OrganizationVO organization = organizationService.create(
                new CreateOrganizationCommand(
                        command.organizationCode(),
                        command.organizationName(),
                        command.organizationType(),
                        null,
                        null,
                        null
                ),
                BOOTSTRAP_ACTOR
        );
        for (Map.Entry<String, String> permission : INITIAL_PERMISSIONS.entrySet()) {
            mapper.upsertPermission(permission.getKey(), permission.getValue());
        }
        long roleId = mapper.createRole("PLATFORM_ADMIN", "平台管理员", BOOTSTRAP_ACTOR);
        long userId = mapper.createUser(
                command,
                passwordEncoder.encode(command.initialPassword()),
                organization.id(),
                BOOTSTRAP_ACTOR
        );
        for (String code : INITIAL_PERMISSIONS.keySet()) {
            mapper.grantPermission(roleId, code, BOOTSTRAP_ACTOR);
        }
        mapper.grantRole(userId, roleId, BOOTSTRAP_ACTOR);
        mapper.grantOrganization(userId, organization.id(), BOOTSTRAP_ACTOR);
        return new BootstrapResultVO(
                userId,
                command.loginName(),
                organization.id(),
                organization.organizationCode(),
                false
        );
    }

    /**
     * 校验当前密码后保存新密码哈希；调用方应在成功后使当前会话失效。
     *
     * @param userId 当前用户主键
     * @param currentPassword 当前密码明文
     * @param newPassword 新密码明文
     */
    @Transactional
    @Override
    public void changePassword(long userId, String currentPassword, String newPassword) {
        UserAccount account = Optional.ofNullable(mapper.findById(userId))
                .filter(UserAccount::enabled)
                .orElseThrow(() -> new AccessDeniedException("Current user is unavailable"));
        if (!passwordEncoder.matches(currentPassword, account.passwordHash())) {
            throw new InvalidRequestException("当前密码不正确");
        }
        rejectPasswordContainingLogin(newPassword, account.loginName());
        if (passwordEncoder.matches(newPassword, account.passwordHash())) {
            throw new InvalidRequestException("新密码不能与当前密码相同");
        }
        if (mapper.changePassword(userId, passwordEncoder.encode(newPassword), account.loginName(), account.version()) != 1) {
            throw new ResourceConflictException("密码状态已被其他操作修改，请重新登录后再试");
        }
    }


    /**
     * 校验一次性启动密钥已安全配置且使用常量时间比较匹配。
     *
     * @param suppliedSecret 请求提供的密钥
     */
    private void validateBootstrapSecret(String suppliedSecret) {
        if (!isBootstrapSecretConfigured() || suppliedSecret == null) {
            throw new AccessDeniedException("当前不能执行平台初始化");
        }
        boolean matches = MessageDigest.isEqual(
                bootstrapSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            throw new AccessDeniedException("平台初始化凭证无效");
        }
    }

    /**
     * 判断部署环境是否提供满足最低长度的一次性启动密钥。
     *
     * @return 已安全配置时为true
     */
    private boolean isBootstrapSecretConfigured() {
        return bootstrapSecret.length() >= MINIMUM_BOOTSTRAP_SECRET_LENGTH;
    }

    /**
     * 禁止密码包含登录名，长度和必填性由入口校验。
     *
     * @param password 待校验密码
     * @param loginName 规范化登录名
     */
    private void rejectPasswordContainingLogin(String password, String loginName) {
        if (password.toLowerCase(Locale.ROOT).contains(loginName.toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException("密码不能包含登录名");
        }
    }

    /**
     * 构造初始管理员可以获得的代码注册权限清单。
     *
     * @return 稳定顺序的权限代码及名称
     */
    private static Map<String, String> initialPermissions() {
        Map<String, String> permissions = new LinkedHashMap<>();
        permissions.put("organization:read", "查询平台机构");
        permissions.put("organization:write", "维护平台机构");
        permissions.put("identity:read", "查询平台用户");
        permissions.put("identity:write", "维护平台用户");
        permissions.put("access:read", "查询角色权限");
        permissions.put("access:write", "维护角色权限");
        permissions.put("configuration:read", "查询平台配置");
        permissions.put("configuration:write", "维护平台配置");
        permissions.put("audit:read", "查询管理审计");
        permissions.put("exchange:read", "查询交换记录");
        permissions.put("master-data:read", "查询基础数据");
        permissions.put("master-data:sync", "发起基础数据同步");
        return Map.copyOf(permissions);
    }
}
