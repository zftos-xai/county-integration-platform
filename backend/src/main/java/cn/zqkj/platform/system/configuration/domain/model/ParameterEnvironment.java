package cn.zqkj.platform.system.configuration.domain.model;

import cn.zqkj.platform.common.utils.Func;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/**
 * 平台参数可绑定的部署环境。
 */
public enum ParameterEnvironment {
    /** 开发环境。 */
    DEVELOPMENT,
    /** 测试环境。 */
    TEST,
    /** 生产环境。 */
    PRODUCTION;

    /**
     * 将管理端提交的环境代码解析为稳定枚举，兼容首尾空白及大小写。
     * @param value 环境代码
     * @return 部署环境；空值交给入口必填约束处理
     */
    @JsonCreator
    public static ParameterEnvironment from(String value) {
        String code = Func.trimToNull(value);
        return code == null ? null : valueOf(code.toUpperCase(Locale.ROOT));
    }
}
