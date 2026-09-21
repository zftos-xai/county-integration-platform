package cn.zqkj.platform.system.organization.mapper;

import cn.zqkj.platform.system.organization.domain.dto.CreateOrganizationCommand;
import cn.zqkj.platform.system.organization.domain.vo.OrganizationVO;
import cn.zqkj.platform.system.organization.domain.dto.UpdateOrganizationCommand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 使用MyBatis读写平台机构表。
 */
@Mapper
public interface OrganizationMapper {

    /**
     * 按主键读取机构。
     *
     * @param id 机构主键
     * @return 机构记录；不存在时为空
     */
    OrganizationVO findById(long id);

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
