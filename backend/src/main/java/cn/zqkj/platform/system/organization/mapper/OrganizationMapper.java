package cn.zqkj.platform.system.organization.mapper;

import cn.zqkj.platform.system.organization.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.organization.domain.dto.UpdateOrganizationCommand;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 使用MyBatis读写平台机构表。
 */
@Mapper
public interface OrganizationMapper {

    /**
     * 在当前事务内串行化机构树修改，防止两个合法父链检查合并成环。
     * @return 非负值表示取得锁；负值表示超时或锁申请失败
     */
    int lockHierarchy();

    /**
     * 按主键读取机构。
     *
     * @param id 机构主键
     * @return 机构记录；不存在时为空
     */
    OrganizationVO findById(long id);

    /**
     * 按机构主键和获准范围读取档案；空范围不匹配任何机构。
     * @param id 机构主键
     * @param organizationCodes 获准访问的机构代码
     * @return 范围内机构；不存在或不可见时为空
     */
    OrganizationVO findVisibleById(@Param("id") long id,
                                    @Param("organizationCodes") List<String> organizationCodes);

    /**
     * 统计指定编码的机构数量。
     *
     * @param organizationCode 机构编码
     * @return 匹配数量
     */
    int countByCode(String organizationCode);

    /**
     * 读取机构列表。
     *
     * @param enabled 可选启用状态
     * @return 机构列表
     */
    List<OrganizationVO> findAll(@Param("enabled") Boolean enabled);

    /**
     * 在SQL中限制机构范围；空范围不返回记录。
     *
     * @param enabled 可选启用状态
     * @param organizationCodes 调用人获准访问的机构代码
     * @return 范围内稳定排序的机构列表
     */
    List<OrganizationVO> findVisible(@Param("enabled") Boolean enabled,
                                     @Param("organizationCodes") List<String> organizationCodes);

    /**
     * 新建机构并回填主键。
     *
     * @param command 已校验创建命令
     * @param actor 操作人标识
     * @return 新机构主键
     */
    long create(@Param("command") CreateOrganizationCommand command, @Param("actor") String actor);

    /**
     * 使用并发版本修改机构。
     *
     * @param id 机构主键
     * @param command 修改命令
     * @param actor 操作人标识
     * @return 实际修改行数
     */
    int update(
            @Param("id") long id,
            @Param("command") UpdateOrganizationCommand command,
            @Param("actor") String actor
    );

    /**
     * 使用并发版本修改机构启用状态。
     *
     * @param id 机构主键
     * @param enabled 目标启用状态
     * @param expectedVersion 并发版本
     * @param actor 操作人标识
     * @return 实际修改行数
     */
    int setEnabled(
            @Param("id") long id,
            @Param("enabled") boolean enabled,
            @Param("expectedVersion") byte[] expectedVersion,
            @Param("actor") String actor
    );

    /**
     * 统计指定父机构下已启用的直接子机构。
     *
     * @param parentId 父机构主键
     * @return 已启用直接子机构数量
     */
    int countEnabledChildren(long parentId);
}
