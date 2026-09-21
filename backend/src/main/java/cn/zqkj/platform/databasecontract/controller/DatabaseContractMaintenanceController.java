package cn.zqkj.platform.databasecontract.controller;

import cn.zqkj.platform.common.core.ApiResponse;
import cn.zqkj.platform.common.exception.InvalidRequestException;
import cn.zqkj.platform.common.utils.Func;
import cn.zqkj.platform.databasecontract.domain.dto.AdvanceDatabaseContractPlanRequest;
import cn.zqkj.platform.databasecontract.domain.dto.ApproveDatabaseContractPlanRequest;
import cn.zqkj.platform.databasecontract.domain.dto.CreateDatabaseContractPlanRequest;
import cn.zqkj.platform.databasecontract.domain.vo.DatabaseContractInspectionVO;
import cn.zqkj.platform.databasecontract.domain.vo.DatabaseContractPlanVO;
import cn.zqkj.platform.databasecontract.service.DatabaseContractMaintenanceService;
import cn.zqkj.platform.framework.security.PlatformUserPrincipal;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 提供健康实例中的数据库契约扫描、方案、审批、执行和取消API。 */
@Validated
@RestController
@RequestMapping("/api/v1/database-contract")
public class DatabaseContractMaintenanceController {

    private final DatabaseContractMaintenanceService service;

    /**
     * 创建数据库契约维护控制器。
     *
     * @param service 数据库契约维护服务
     */
    public DatabaseContractMaintenanceController(DatabaseContractMaintenanceService service) {
        this.service = service;
    }

    /**
     * 扫描当前数据库、迁移契约、Mapper和Java模型的一致性。
     *
     * <p>需要 {@code database-contract:read} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @return 当前实时契约差异
     */
    @GetMapping("/inspection")
    @PreAuthorize("hasAuthority('database-contract:read')")
    public ApiResponse<DatabaseContractInspectionVO> inspect() {
        return ApiResponse.success(service.inspect());
    }

    /**
     * 查询最近的数据库契约维护方案。
     *
     * <p>需要 {@code database-contract:read} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @return 最近维护方案
     */
    @GetMapping("/plans")
    @PreAuthorize("hasAuthority('database-contract:read')")
    public ApiResponse<List<DatabaseContractPlanVO>> findPlans() {
        return ApiResponse.success(service.findRecentPlans());
    }

    /**
     * 按主键读取数据库契约维护方案及明细。
     *
     * <p>需要 {@code database-contract:read} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param id 方案主键
     * @return 方案详情
     */
    @GetMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('database-contract:read')")
    public ApiResponse<DatabaseContractPlanVO> getPlan(@PathVariable @Positive long id) {
        return ApiResponse.success(service.getPlan(id));
    }

    /**
     * 根据当前仍存在的契约差异创建维护方案。
     *
     * <p>需要 {@code database-contract:plan} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param request 创建请求
     * @param principal 当前用户
     * @return 新方案
     */
    @PostMapping("/plans")
    @PreAuthorize("hasAuthority('database-contract:plan')")
    public ApiResponse<DatabaseContractPlanVO> createPlan(
            @Valid @RequestBody CreateDatabaseContractPlanRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal) {
        return ApiResponse.success(service.createPlan(request, actor(principal)));
    }

    /**
     * 审批尚未执行的数据库契约维护方案。
     *
     * <p>需要 {@code database-contract:approve} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param id 主键
     * @param request 审批请求
     * @param principal 当前用户
     * @return 已批准方案
     */
    @PostMapping("/plans/{id}/approve")
    @PreAuthorize("hasAuthority('database-contract:approve')")
    public ApiResponse<DatabaseContractPlanVO> approve(
            @PathVariable @Positive long id,
            @Valid @RequestBody ApproveDatabaseContractPlanRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal) {
        return ApiResponse.success(service.approve(
                id, decodeVersion(request.version()), request.note(), actor(principal)));
    }

    /**
     * 执行已批准方案并在同一受控流程中完成复验。
     *
     * <p>需要 {@code database-contract:execute} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param id 主键
     * @param request 并发版本
     * @param principal 当前用户
     * @return 执行和复验结果
     */
    @PostMapping("/plans/{id}/execute")
    @PreAuthorize("hasAuthority('database-contract:execute')")
    public ApiResponse<DatabaseContractPlanVO> execute(
            @PathVariable @Positive long id,
            @Valid @RequestBody AdvanceDatabaseContractPlanRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal) {
        return ApiResponse.success(service.execute(id, decodeVersion(request.version()), actor(principal)));
    }

    /**
     * 在执行开始前取消维护方案。
     *
     * <p>需要 {@code database-contract:plan} 功能权限；资源范围和业务规则仍由服务层校验。</p>
     *
     * @param id 主键
     * @param request 并发版本
     * @param principal 当前用户
     * @return 已取消方案
     */
    @PostMapping("/plans/{id}/cancel")
    @PreAuthorize("hasAuthority('database-contract:plan')")
    public ApiResponse<DatabaseContractPlanVO> cancel(
            @PathVariable @Positive long id,
            @Valid @RequestBody AdvanceDatabaseContractPlanRequest request,
            @AuthenticationPrincipal PlatformUserPrincipal principal) {
        return ApiResponse.success(service.cancel(id, decodeVersion(request.version()), actor(principal)));
    }

    /**
     * 解码并校验客户端提交的SQL Server行版本。
     *
     * @param value Base64行版本
     * @return 8字节SQL Server行版本
     */
    private byte[] decodeVersion(String value) {
        try {
            byte[] version = Func.decodeBase64(value);
            if (version.length != Long.BYTES) {
                throw new InvalidRequestException("version 必须表示8字节SQL Server行版本");
            }
            return version;
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("version 必须是有效Base64文本");
        }
    }

    /**
     * 将当前登录主体转换为携带机构范围的服务层操作人。
     *
     * @param principal 当前用户
     * @return 服务层操作人
     */
    private AccessActor actor(PlatformUserPrincipal principal) {
        return new AccessActor(principal.userId(), principal.getUsername(), principal.organizationCodes());
    }
}
