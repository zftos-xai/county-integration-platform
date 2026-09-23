package cn.zqkj.platform.masterdata.mapper.icd10;

import cn.zqkj.platform.masterdata.domain.icd10.dto.Icd10DirectoryQuery;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DirectoryRecord;
import cn.zqkj.platform.masterdata.domain.icd10.vo.Icd10DirectoryCountVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 只读查询已经发布的平台公共ICD10目录。 */
@Mapper
public interface Icd10DirectoryCatalogMapper {
    /**
     * 统计当前筛选后的公共目录。
     *
     * @param query 查询条件
     * @return 当前筛选总数
     */
    long countPage(@Param("query") Icd10DirectoryQuery query);
    /**
     * 查询当前页公共目录记录。
     *
     * @param query 查询条件
     * @return 当前页公共目录记录
     */
    List<Icd10DirectoryRecord> findPage(@Param("query") Icd10DirectoryQuery query);
    /** @return 按西医、中医类别的当前目录数量。 */
    List<Icd10DirectoryCountVO> countByCategory();
}
