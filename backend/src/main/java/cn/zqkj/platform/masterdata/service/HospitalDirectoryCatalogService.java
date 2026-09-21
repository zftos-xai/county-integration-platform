package cn.zqkj.platform.masterdata.service;

import cn.zqkj.platform.masterdata.domain.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.vo.HospitalDirectoryPageVO;
import cn.zqkj.platform.system.domain.model.AccessActor;

/** 提供权限范围内的当前医院综合目录只读查询。 */
public interface HospitalDirectoryCatalogService {

    /**
     * 按机构权限、目录类型和关键字分页查询当前有效医院目录。
     *
     * @param query 查询条件
     * @param actor 当前用户
     * @return 当前有效目录分页
     */
    HospitalDirectoryPageVO findPage(HospitalDirectoryQuery query, AccessActor actor);
}
