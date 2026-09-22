package cn.zqkj.platform.system.identity.domain.model;

import java.util.Set;

/**
 * 表示从服务端登录用户解析出的管理操作人和机构范围。
 *
 * @param userId 操作人用户主键
 * @param loginName 操作人登录名
 * @param organizationCodes 操作人当前机构范围代码
 */
public record AccessActor(long userId, String loginName, Set<String> organizationCodes) {

    /**
     * 防止调用方持有可变机构范围集合。
     *
     * @param userId 操作人用户主键
     * @param loginName 操作人登录名
     * @param organizationCodes 操作人当前机构范围代码
     */
    public AccessActor {
        organizationCodes = Set.copyOf(organizationCodes);
    }

}
