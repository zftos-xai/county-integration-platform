package cn.zqkj.platform.masterdata.mapper.medicaldirectory;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectoryCountVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 只读查询已经完成来源数量核对和自动更新的医疗目录及其来源链路。
 */
@Mapper
public interface MedicalDirectoryCatalogMapper {

    /**
     * 按当前账号机构范围统计满足筛选条件的有效目录总数。
     *
     * @param query 查询条件
     * @param organizationCodes 当前账号机构范围
     * @return 筛选后的目录总数
     */
    long countPage(@Param("query") MedicalDirectoryQuery query,
                   @Param("organizationCodes") List<String> organizationCodes);

    /**
     * 按有界分页条件查询医疗目录。
     *
     * @param query 查询条件
     * @param organizationCodes 当前账号机构范围
     * @return 当前页有效目录
     */
    List<MedicalDirectoryRecord> findPage(@Param("query") MedicalDirectoryQuery query,
                                          @Param("organizationCodes") List<String> organizationCodes);

    /**
     * 统计四类医疗目录当前有效记录数。
     *
     * @param organizationCode 可选机构筛选
     * @param organizationCodes 当前账号机构范围
     * @return 按类型汇总的当前有效记录数
     */
    List<MedicalDirectoryCountVO> countByType(@Param("organizationCode") String organizationCode,
                                                @Param("organizationCodes") List<String> organizationCodes);
}
