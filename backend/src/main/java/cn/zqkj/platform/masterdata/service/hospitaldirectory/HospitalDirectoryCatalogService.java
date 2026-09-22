package cn.zqkj.platform.masterdata.service.hospitaldirectory;

import cn.zqkj.platform.masterdata.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectoryPageVO;
import java.util.List;

/** 按调用方明确提供的机构范围查询当前医院综合目录。 */
public interface HospitalDirectoryCatalogService {

    /**
     * 按机构范围、目录类型和关键字分页查询当前有效医院目录。
     *
     * @param query 查询条件
     * @param allowedOrganizationCodes 已在调用入口确认的机构范围；空集合返回空结果
     * @return 当前有效目录分页
     */
    HospitalDirectoryPageVO findPage(HospitalDirectoryQuery query, List<String> allowedOrganizationCodes);
}
