package cn.zqkj.platform.system.identity.application;

/**
 * 表示平台一次性安全引导状态。
 *
 * @param initialized 是否已经存在平台用户
 * @param bootstrapAvailable 是否仍可使用启动密钥执行引导
 */
public record BootstrapStatus(boolean initialized, boolean bootstrapAvailable) {
}
