package cn.zqkj.platform.masterdata.service.medicaldirectory;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectoryPageVO;
import java.util.List;

/**
 * 按调用方明确提供的机构范围查询当前已自动更新医疗目录。
 */
public interface MedicalDirectoryCatalogService {

    /**
     * 按给定机构范围读取当前有效医疗目录及可追溯来源。
     *
     * @param query 有界查询条件
     * @param allowedOrganizationCodes 已在调用入口确认的机构范围；空集合返回空结果
     * @return 当前有效医疗目录分页
     */
    MedicalDirectoryPageVO findPage(MedicalDirectoryQuery query, List<String> allowedOrganizationCodes);
}
