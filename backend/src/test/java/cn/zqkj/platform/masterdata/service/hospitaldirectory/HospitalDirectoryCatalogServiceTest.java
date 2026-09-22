package cn.zqkj.platform.masterdata.service.hospitaldirectory;

import cn.zqkj.platform.masterdata.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryRecord;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryType;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectoryCountVO;
import cn.zqkj.platform.masterdata.mapper.hospitaldirectory.HospitalDirectoryCatalogMapper;
import cn.zqkj.platform.masterdata.service.hospitaldirectory.impl.HospitalDirectoryCatalogServiceImpl;
import jakarta.validation.Validation;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证正式医院综合目录的机构范围、分页和来源批次输出。 */
class HospitalDirectoryCatalogServiceTest {

    /** 直接调用业务服务时，查询对象仍须具有与HTTP入口一致的有效边界。 */
    @Test
    void queryIsCanonicalAtCreation() {
        HospitalDirectoryQuery query = new HospitalDirectoryQuery(" ORG001 ", null, "  ", 1, 20);

        assertEquals("ORG001", query.organizationCode());
        assertNull(query.keyword());
    }

    /** 查询对象承载分页默认值，入口使用同一对象的声明式约束拒绝非法参数。 */
    @Test
    void defaultsAndValidatesQueryAtEntry() {
        HospitalDirectoryQuery query = new HospitalDirectoryQuery(null, null, null, null, null);
        assertEquals(1, query.page());
        assertEquals(20, query.pageSize());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new HospitalDirectoryQuery(null, null, null, 0, 20)).isEmpty());
            assertFalse(validator.validate(new HospitalDirectoryQuery(null, null, null, 1, 101)).isEmpty());
            assertFalse(validator.validate(new HospitalDirectoryQuery(null, null, "x".repeat(51), 1, 20)).isEmpty());
        }
    }

    /** 验证只返回当前账号机构范围内的真实有效目录和类型数量。 */
    @Test
    void returnsVisiblePublishedDirectoryWithSourceBatch() {
        HospitalDirectoryCatalogMapper mapper = mock(HospitalDirectoryCatalogMapper.class);
        HospitalDirectoryCatalogService service = new HospitalDirectoryCatalogServiceImpl(mapper);
        HospitalDirectoryQuery query = new HospitalDirectoryQuery(
                " ORG001 ", HospitalDirectoryType.DOCTOR, " 张 ", 1, 20);
        LocalDateTime synchronizedAt = LocalDateTime.of(2026, 9, 20, 8, 30);
        when(mapper.countPage(any(), eq(List.of("ORG001")))).thenReturn(1L);
        when(mapper.findPage(any(), eq(List.of("ORG001")))).thenReturn(List.of(
                new HospitalDirectoryRecord(
                        9L, "ORG001", "测试机构", HospitalDirectoryType.DOCTOR, "D001", "张医生",
                        "ZYS", null, null, "ORG001", 2L, 25L, "BD-TEST", synchronizedAt)));
        when(mapper.countByType("ORG001", List.of("ORG001"))).thenReturn(List.of(
                new HospitalDirectoryCountVO(HospitalDirectoryType.DOCTOR, 1L)));

        var result = service.findPage(query, List.of("ORG001"));

        assertEquals(1L, result.total());
        assertEquals("BD-TEST", result.items().get(0).latestBatchNo());
        assertEquals(2L, result.items().get(0).relationCount());
        assertEquals(HospitalDirectoryType.DOCTOR, result.counts().get(0).directoryType());
        verify(mapper).countByType("ORG001", List.of("ORG001"));
    }

    /** 验证空机构范围始终传给查询边界，不能退化成全机构查询。 */
    @Test
    void preservesEmptyOrganizationScope() {
        HospitalDirectoryCatalogMapper mapper = mock(HospitalDirectoryCatalogMapper.class);
        when(mapper.findPage(any(), eq(List.of()))).thenReturn(List.of());
        HospitalDirectoryCatalogService service = new HospitalDirectoryCatalogServiceImpl(mapper);

        service.findPage(new HospitalDirectoryQuery(null, null, null, 1, 20), List.of());

        verify(mapper).countPage(any(), eq(List.of()));
        verify(mapper).findPage(any(), eq(List.of()));
    }
}
