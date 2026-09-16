package cn.zqkj.platform.system.identity.api;

import cn.zqkj.platform.foundation.web.ApiResponse;
import cn.zqkj.platform.system.identity.application.BootstrapCommand;
import cn.zqkj.platform.system.identity.application.BootstrapResult;
import cn.zqkj.platform.system.identity.application.BootstrapStatus;
import cn.zqkj.platform.system.identity.application.IdentityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供平台一次性安全引导状态和执行入口。
 */
@RestController
@RequestMapping("/api/v1/bootstrap")
public class BootstrapController {

    private final IdentityService service;

    /**
     * 创建安全引导控制器。
     *
     * @param service 身份应用服务
     */
    public BootstrapController(IdentityService service) {
        this.service = service;
    }

    /** @return 不包含启动密钥内容的安全引导状态 */
    @GetMapping("/status")
    public ApiResponse<BootstrapStatus> status() {
        return ApiResponse.success(service.getBootstrapStatus());
    }

    /**
     * 执行一次性安全引导。
     *
     * @param bootstrapSecret 部署环境安全交付的启动密钥
     * @param request 引导请求
     * @return 创建的初始身份关系
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BootstrapResult> bootstrap(
            @RequestHeader("X-Platform-Bootstrap-Secret") String bootstrapSecret,
            @Valid @RequestBody BootstrapRequest request
    ) {
        BootstrapCommand command = new BootstrapCommand(
                request.organizationCode(), request.organizationName(), request.organizationType(),
                request.loginName(), request.displayName(), request.initialPassword()
        );
        return ApiResponse.success(service.bootstrap(bootstrapSecret, command));
    }
}
