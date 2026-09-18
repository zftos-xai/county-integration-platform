package cn.zqkj.platform.system.domain.vo;

/**
 * 平台基础运行信息。
 *
 * @param application 应用名称
 * @param runtime Java运行时版本
 * @param status 当前工程状态
 */
public record PlatformInfoVO(String application, String runtime, String status) {
}
