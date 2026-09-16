package cn.zqkj.platform.system.domain.vo;

/**
 * 表示平台一次性安全引导状态。
 *
 * @param initialized 是否已经存在平台用户
 * @param bootstrapAvailable 是否仍可使用启动密钥执行引导
 */
public record BootstrapStatusVO(boolean initialized, boolean bootstrapAvailable) {
}
