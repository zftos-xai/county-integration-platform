package cn.zqkj.platform.common.core;

/**
 * 平台管理 API 的统一成功响应结构。
 *
 * @param code 稳定的业务结果代码
 * @param message 面向调用方的结果说明
 * @param data 响应业务数据
 * @param <T> 响应数据类型
 */
public record ApiResponse<T>(String code, String message, T data) {

    /**
     * 创建成功响应。
     *
     * @param data 响应业务数据
     * @param <T> 响应数据类型
     * @return 结果代码为 {@code SUCCESS} 的响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "处理成功", data);
    }
}
