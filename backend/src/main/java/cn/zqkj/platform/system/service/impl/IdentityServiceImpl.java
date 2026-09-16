package cn.zqkj.platform.system.service.impl;

import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.exception.ResourceConflictException;
import cn.zqkj.platform.system.domain.dto.BootstrapCommand;
import cn.zqkj.platform.system.domain.vo.BootstrapResultVO;
import cn.zqkj.platform.system.domain.vo.BootstrapStatusVO;
import cn.zqkj.platform.system.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.domain.model.UserAccount;
import cn.zqkj.platform.system.mapper.IdentityMapper;
import cn.zqkj.platform.system.service.IdentityService;
import cn.zqkj.platform.system.service.OrganizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 实现平台一次性安全引导、初始管理员创建和本地密码修改。
 */
@Service
public class IdentityServiceImpl implements IdentityService {

    private static final String BOOTSTRAP_ACTOR = "platform-bootstrap";
    private static final int MINIMUM_BOOTSTRAP_SECRET_LENGTH = 32;
    private static final int MINIMUM_PASSWORD_LENGTH = 12;
    private static final int MAXIMUM_PASSWORD_LENGTH = 128;
    private static final Pattern LOGIN_PATTERN = Pattern.compile("[A-Za-z0-9._-]{3,64}");
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
        BootstrapCommand normalized = normalize(command);
        if (mapper.lockAndCountUsers() > 0) {
            throw new ResourceConflictException("Platform bootstrap has already completed");
        }

        OrganizationVO organization = organizationService.create(
                new CreateOrganizationCommand(
                        normalized.organizationCode(),
                        normalized.organizationName(),
                        normalized.organizationType(),
                        null,
                        null,
                        null
                ),
                BOOTSTRAP_ACTOR
        );
        INITIAL_PERMISSIONS.forEach(mapper::upsertPermission);
        long roleId = mapper.createRole("PLATFORM_ADMIN", "平台管理员", BOOTSTRAP_ACTOR);
        long userId = mapper.createUser(
                normalized,
                passwordEncoder.encode(normalized.initialPassword()),
                organization.id(),
                BOOTSTRAP_ACTOR
        );
        INITIAL_PERMISSIONS.keySet().forEach(code -> mapper.grantPermission(roleId, code, BOOTSTRAP_ACTOR));
        mapper.grantRole(userId, roleId, BOOTSTRAP_ACTOR);
        mapper.grantOrganization(userId, organization.id(), BOOTSTRAP_ACTOR);
        return new BootstrapResultVO(
                userId,
                normalized.loginName(),
                organization.id(),
                organization.organizationCode(),
                true
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
        UserAccount account = java.util.Optional.ofNullable(mapper.findById(userId))
                .filter(UserAccount::enabled)
                .orElseThrow(() -> new AccessDeniedException("Current user is unavailable"));
        if (!passwordEncoder.matches(currentPassword, account.passwordHash())) {
            throw new InvalidRequestException("Current password does not match");
        }
        validatePassword(newPassword, account.loginName());
        if (passwordEncoder.matches(newPassword, account.passwordHash())) {
            throw new InvalidRequestException("New password must differ from current password");
        }
        if (mapper.changePassword(userId, passwordEncoder.encode(newPassword), account.loginName()) != 1) {
            throw new ResourceConflictException("User password state changed concurrently");
        }
    }

    /**
     * 规范化并校验安全引导请求。
     *
     * @param command 原始安全引导请求
     * @return 规范化请求
     */
    private BootstrapCommand normalize(BootstrapCommand command) {
        if (command == null) {
            throw new InvalidRequestException("Bootstrap request is required");
        }
        String loginName = requireText(command.loginName(), "loginName", 64).toLowerCase(Locale.ROOT);
        if (!LOGIN_PATTERN.matcher(loginName).matches()) {
            throw new InvalidRequestException("loginName has an invalid format");
        }
        validatePassword(command.initialPassword(), loginName);
        return new BootstrapCommand(
                requireText(command.organizationCode(), "organizationCode", 64),
                requireText(command.organizationName(), "organizationName", 200),
                requireText(command.organizationType(), "organizationType", 32),
                loginName,
                requireText(command.displayName(), "displayName", 100),
                command.initialPassword()
        );
    }

    /**
     * 校验一次性启动密钥已安全配置且使用常量时间比较匹配。
     *
     * @param suppliedSecret 请求提供的密钥
     */
    private void validateBootstrapSecret(String suppliedSecret) {
        if (!isBootstrapSecretConfigured() || suppliedSecret == null) {
            throw new AccessDeniedException("Platform bootstrap is unavailable");
        }
        boolean matches = MessageDigest.isEqual(
                bootstrapSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            throw new AccessDeniedException("Platform bootstrap credential is invalid");
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
     * 校验密码长度并禁止包含登录名。
     *
     * @param password 待校验密码
     * @param loginName 规范化登录名
     */
    private void validatePassword(String password, String loginName) {
        if (password == null
                || password.length() < MINIMUM_PASSWORD_LENGTH
                || password.length() > MAXIMUM_PASSWORD_LENGTH) {
            throw new InvalidRequestException("Password length is outside the allowed range");
        }
        if (password.toLowerCase(Locale.ROOT).contains(loginName.toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException("Password must not contain the login name");
        }
    }

    /**
     * 校验并裁剪受控文本。
     *
     * @param value 输入文本
     * @param field 字段名
     * @param maximumLength 最大长度
     * @return 裁剪后的文本
     */
    private String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.trim().length() > maximumLength) {
            throw new InvalidRequestException(field + " is invalid");
        }
        return value.trim();
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
        return Map.copyOf(permissions);
    }
}
