package cn.zqkj.platform.system.api;

import cn.zqkj.platform.foundation.web.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 提供平台自身的只读基础信息接口。
 *
 * <p>该接口不读取患者或业务交换数据。</p>
 */
@RestController
@RequestMapping("/api/v1/system")
public class PlatformInfoController {

    private final String applicationName;

    /**
     * 创建平台信息控制器。
     *
     * @param applicationName 配置中的 Spring 应用名称
     */
    public PlatformInfoController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * 返回应用名称、Java 运行时基线和当前工程状态。
     *
     * @return 只包含平台基础信息的统一成功响应
     */
    @GetMapping("/info")
    ApiResponse<Map<String, String>> info() {
        return ApiResponse.success(Map.of(
                "application", applicationName,
                "runtime", "Java 17",
                "status", "BASELINE_READY"
        ));
    }
}
