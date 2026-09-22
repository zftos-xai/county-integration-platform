package cn.zqkj.platform.masterdata.service.medicaldirectory;

import cn.zqkj.platform.masterdata.domain.medicaldirectory.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryRecord;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryType;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectoryCountVO;
import cn.zqkj.platform.masterdata.mapper.medicaldirectory.MedicalDirectoryCatalogMapper;
import cn.zqkj.platform.masterdata.service.medicaldirectory.impl.MedicalDirectoryCatalogServiceImpl;
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

/**
 * 验证医疗目录只读取当前有效数据，并保留来源批次和调用机构参数链路。
 */
class MedicalDirectoryCatalogServiceTest {

    /** 内部调用创建的查询对象与HTTP入口使用同一文本和分页约束。 */
    @Test
    void queryIsCanonicalAtCreation() {
        MedicalDirectoryQuery query = new MedicalDirectoryQuery(" ORG001 ", null, "  ", 1, 20);

        assertEquals("ORG001", query.organizationCode());
        assertNull(query.keyword());
    }

    /** 查询对象承载分页默认值，入口使用同一对象的声明式约束拒绝非法参数。 */
    @Test
    void defaultsAndValidatesQueryAtEntry() {
        MedicalDirectoryQuery query = new MedicalDirectoryQuery(null, null, null, null, null);
        assertEquals(1, query.page());
        assertEquals(20, query.pageSize());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new MedicalDirectoryQuery(null, null, null, 0, 20)).isEmpty());
            assertFalse(validator.validate(new MedicalDirectoryQuery(null, null, null, 1, 101)).isEmpty());
            assertFalse(validator.validate(new MedicalDirectoryQuery(null, null, "x".repeat(51), 1, 20)).isEmpty());
        }
    }

    /** 验证当前账号范围内医疗目录的分页、类型数量和来源参数输出。 */
    @Test
    void returnsVisibleCurrentDirectoryWithSourceBatch() {
        MedicalDirectoryCatalogMapper mapper = mock(MedicalDirectoryCatalogMapper.class);
        MedicalDirectoryCatalogService service = new MedicalDirectoryCatalogServiceImpl(mapper);
        MedicalDirectoryQuery query = new MedicalDirectoryQuery(
                " ORG001 ", MedicalDirectoryType.WESTERN_MEDICINE, " 阿莫 ", 1, 20);
        LocalDateTime synchronizedAt = LocalDateTime.of(2026, 9, 21, 8, 30);
        when(mapper.countPage(any(), eq(List.of("ORG001")))).thenReturn(1L);
        when(mapper.findPage(any(), eq(List.of("ORG001")))).thenReturn(List.of(
                new MedicalDirectoryRecord(
                        9L, "ORG001", "测试机构", MedicalDirectoryType.WESTERN_MEDICINE,
                        "W001", "阿莫西林", "AMXL", "西药", "盒", "0.25g", "测试厂家", "启用",
                        "2026-09-20", 25L, "BD-TEST", "HIS-ORG-01", synchronizedAt)));
        when(mapper.countByType("ORG001", List.of("ORG001"))).thenReturn(List.of(
                new MedicalDirectoryCountVO(MedicalDirectoryType.WESTERN_MEDICINE, 1L)));

        var result = service.findPage(query, List.of("ORG001"));

        assertEquals(1L, result.total());
        assertEquals("BD-TEST", result.items().get(0).latestBatchNo());
        assertEquals("HIS-ORG-01", result.items().get(0).sourceOrganizationId());
        assertEquals(MedicalDirectoryType.WESTERN_MEDICINE, result.counts().get(0).directoryType());
        verify(mapper).countByType("ORG001", List.of("ORG001"));
    }

    /** 验证空机构范围始终传给查询边界，不能退化成全机构查询。 */
    @Test
    void preservesEmptyOrganizationScope() {
        MedicalDirectoryCatalogMapper mapper = mock(MedicalDirectoryCatalogMapper.class);
        when(mapper.findPage(any(), eq(List.of()))).thenReturn(List.of());
        MedicalDirectoryCatalogService service = new MedicalDirectoryCatalogServiceImpl(mapper);

        service.findPage(new MedicalDirectoryQuery(null, null, null, 1, 20), List.of());

        verify(mapper).countPage(any(), eq(List.of()));
        verify(mapper).findPage(any(), eq(List.of()));
    }
}
