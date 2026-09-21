package cn.zqkj.platform.his.domain.protocol.model;

/**
 * 基层HIS统一业务响应。
 *
 * @param success {@code result/Result}为1时为真
 * @param resultCode 规范化后的目标结果码，目前只接受0或1
 * @param data 成功时按具体交易转换后的业务数据
 * @param errorMessage 失败时目标系统返回的简短错误说明
 * @param <T> 具体交易的业务数据类型
 */
public record PhisResponse<T>(boolean success, String resultCode, T data, String errorMessage) {

    /**
     * 校验成功和失败响应的字段不变量。
     *
     * @param success 是否为HIS明确成功结果
     * @param resultCode HIS结果码
     * @param data 成功业务数据
     * @param errorMessage 失败错误说明
     */
    public PhisResponse {
        if (resultCode == null || resultCode.isBlank()) {
            throw new IllegalArgumentException("HIS响应必须包含结果码");
        }
        if (success) {
            if (data == null || errorMessage != null) {
                throw new IllegalArgumentException("HIS成功响应必须包含数据且不能包含错误说明");
            }
        } else if (data != null || errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("HIS失败响应不能包含数据且必须包含错误说明");
        }
    }

    /**
     * 创建字段状态一致的成功响应。
     *
     * @param resultCode HIS成功结果码
     * @param data 非空业务数据
     * @param <T> 业务数据类型
     * @return 成功响应
     */
    public static <T> PhisResponse<T> success(String resultCode, T data) {
        return new PhisResponse<>(true, resultCode, data, null);
    }

    /**
     * 创建字段状态一致的明确业务失败响应。
     *
     * @param resultCode HIS失败结果码
     * @param errorMessage 不含凭证和报文正文的失败说明
     * @param <T> 业务数据类型
     * @return 失败响应
     */
    public static <T> PhisResponse<T> failure(String resultCode, String errorMessage) {
        return new PhisResponse<>(false, resultCode, null, errorMessage);
    }
}
