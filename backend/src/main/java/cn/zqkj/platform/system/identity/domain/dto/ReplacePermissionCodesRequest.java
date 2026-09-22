package cn.zqkj.platform.system.identity.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 承载替换角色功能权限请求。
 *
 * @param permissionCodes 目标代码注册权限
 */
public record ReplacePermissionCodesRequest(
        @NotNull List<@NotBlank String> permissionCodes
) {
    /**
     * 统一权限代码两端空白，保留无效元素供入口约束拒绝。
     * @param permissionCodes 目标权限代码
     */
    public ReplacePermissionCodesRequest {
        if (permissionCodes != null) {
            List<String> codes = new ArrayList<>(permissionCodes.size());
            for (String code : permissionCodes) {
                codes.add(Func.trimToNull(code));
            }
            permissionCodes = Collections.unmodifiableList(codes);
        }
    }
}
