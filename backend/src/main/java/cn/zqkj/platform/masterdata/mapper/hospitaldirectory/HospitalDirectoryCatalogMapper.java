package cn.zqkj.platform.masterdata.mapper.hospitaldirectory;

import cn.zqkj.platform.masterdata.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryRecord;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectoryCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 只读查询已经自动完成对账的医院综合目录及其来源链路。 */
@Mapper
public interface HospitalDirectoryCatalogMapper {

    /**
     * 按当前账号机构范围统计满足筛选条件的有效目录总数。
     *
     * @param query 查询条件
     * @param organizationCodes 当前账号机构范围
     * @return 筛选后的目录总数
     */
    long countPage(
            @Param("query") HospitalDirectoryQuery query,
            @Param("organizationCodes") List<String> organizationCodes
    );

    /**
     * 按有界分页条件查询医院综合目录。
     *
     * @param query 查询条件
     * @param organizationCodes 当前账号机构范围
     * @return 当前页有效目录
     */
    List<HospitalDirectoryRecord> findPage(
            @Param("query") HospitalDirectoryQuery query,
            @Param("organizationCodes") List<String> organizationCodes
    );

    /**
     * 统计四类目录数量。
     *
     * @param organizationCode 可选机构筛选
     * @param organizationCodes 当前账号机构范围
     * @return 四类目录数量
     */
    List<HospitalDirectoryCountVO> countByType(
            @Param("organizationCode") String organizationCode,
            @Param("organizationCodes") List<String> organizationCodes
    );
}
