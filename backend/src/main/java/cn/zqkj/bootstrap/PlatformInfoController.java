package cn.zqkj.bootstrap;

import cn.zqkj.shared.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class PlatformInfoController {

    private final String applicationName;

    public PlatformInfoController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/info")
    ApiResponse<Map<String, String>> info() {
        return ApiResponse.success(Map.of(
                "application", applicationName,
                "runtime", "Java 17",
                "status", "BASELINE_READY"
        ));
    }
}
