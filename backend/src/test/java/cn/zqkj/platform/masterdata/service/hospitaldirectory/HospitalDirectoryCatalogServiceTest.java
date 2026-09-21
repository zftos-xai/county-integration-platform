package cn.zqkj.platform.masterdata.service.hospitaldirectory;

import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryType;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryRecord;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryTypeCount;
import cn.zqkj.platform.masterdata.mapper.hospitaldirectory.HospitalDirectoryCatalogMapper;
import cn.zqkj.platform.masterdata.service.hospitaldirectory.impl.HospitalDirectoryCatalogServiceImpl;
import cn.zqkj.platform.system.identity.domain.model.AccessActor;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证正式医院综合目录的机构范围、分页和来源批次输出。 */
class HospitalDirectoryCatalogServiceTest {

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
                new HospitalDirectoryTypeCount(HospitalDirectoryType.DOCTOR, 1L)));

        var result = service.findPage(query, actor());

        assertEquals(1L, result.total());
        assertEquals("BD-TEST", result.items().get(0).latestBatchNo());
        assertEquals(2L, result.items().get(0).relationCount());
        assertEquals(HospitalDirectoryType.DOCTOR, result.counts().get(0).directoryType());
        verify(mapper).countByType("ORG001", List.of("ORG001"));
    }

    /** 验证不能通过筛选参数读取账号范围外机构。 */
    @Test
    void rejectsOrganizationOutsideActorScope() {
        HospitalDirectoryCatalogService service = new HospitalDirectoryCatalogServiceImpl(
                mock(HospitalDirectoryCatalogMapper.class));

        assertThrows(AccessDeniedException.class, () -> service.findPage(
                new HospitalDirectoryQuery("ORG002", null, null, 1, 20), actor()));
    }

    /**
     * 创建具有测试机构范围的服务层操作人。
     *
     * @return 只有测试机构范围的操作人
     */
    private AccessActor actor() {
        return new AccessActor(1L, "admin", Set.of("ORG001"));
    }
}
