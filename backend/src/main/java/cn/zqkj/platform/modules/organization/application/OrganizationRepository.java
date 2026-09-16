package cn.zqkj.platform.modules.organization.application;

import java.util.List;
import java.util.Optional;

/**
 * 定义平台机构的持久化边界。
 */
public interface OrganizationRepository {

    /**
     * 按主键读取机构。
     *
     * @param id 机构主键
     * @return 机构快照；不存在时为空
     */
    Optional<OrganizationView> findById(long id);

    /**
     * 按编码判定机构是否存在。
     *
     * @param organizationCode 机构编码
     * @return 存在时为true
     */
    boolean existsByCode(String organizationCode);

    /**
     * 读取机构列表。
     *
     * @param enabled 可选启用状态；空值表示不过滤
     * @return 按名称和主键排序的机构列表
     */
    List<OrganizationView> findAll(Boolean enabled);

    /**
     * 新建机构。
     *
     * @param command 已校验创建命令
     * @param actor 操作主体稳定标识
     * @return 持久化后的机构快照
     */
    OrganizationView create(CreateOrganizationCommand command, String actor);

    /**
     * 使用并发版本修改机构。
     *
     * @param id 机构主键
     * @param command 已校验修改命令
     * @param actor 操作主体稳定标识
     * @return 实际修改行数
     */
    int update(long id, UpdateOrganizationCommand command, String actor);

    /**
     * 使用并发版本修改机构启用状态。
     *
     * @param id 机构主键
     * @param enabled 目标启用状态
     * @param expectedVersion 客户端上次读取的并发版本
     * @param actor 操作主体稳定标识
     * @return 实际修改行数
     */
    int setEnabled(long id, boolean enabled, byte[] expectedVersion, String actor);

    /**
     * 判定机构是否存在已启用的直接子机构。
     *
     * @param parentId 父机构主键
     * @return 存在时为true
     */
    boolean hasEnabledChildren(long parentId);
}
