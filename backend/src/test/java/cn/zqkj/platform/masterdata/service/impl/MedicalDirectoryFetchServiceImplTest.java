package cn.zqkj.platform.masterdata.service.impl;

import cn.zqkj.platform.his.domain.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.his.domain.model.MedicalDirectoryEntry;
import cn.zqkj.platform.his.domain.model.MedicalDirectoryType;
import cn.zqkj.platform.his.domain.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisBusinessException;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证目录完整取得不会漏页，也不会在来源明确拒绝后继续取得。 */
class MedicalDirectoryFetchServiceImplTest {

    private static final LocalDateTime RANGE_START = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime RANGE_END = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    /** 验证101条来源数据按100条一页的范围完整取回，并交给一次自动校验。 */
    @Test
    void fetchesEveryPageBeforeSingleValidation() {
        PhisService phisService = mock(PhisService.class);
        when(phisService.countMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "1", 101L, null));
        when(phisService.queryMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any()))
                .thenAnswer(invocation -> {
                    MedicalDirectoryQuery query = invocation.getArgument(2);
                    if (query.startRow() == 1) {
                        return new PhisResponse<>(true, "1", entries(1, 100), null);
                    }
                    return new PhisResponse<>(true, "1", entries(101, 101), null);
                });
        MedicalDirectoryFetchServiceImpl service = new MedicalDirectoryFetchServiceImpl(
                phisService, new MedicalDirectoryValidationServiceImpl());

        var result = service.fetchAll(8L, ParameterEnvironment.TEST, MedicalDirectoryType.CONSUMABLE,
                RANGE_START, RANGE_END, "ORG-008");

        assertTrue(result.validation().valid());
        assertEquals(101, result.validation().declaredCount());
        assertEquals(101, result.validation().returnedCount());
        assertEquals(101, result.validation().acceptedRecords().size());
        verify(phisService, times(2)).queryMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any());
    }

    /** 验证来源明确拒绝第二页时立即停止，不能把不完整结果交给后续暂存或发布。 */
    @Test
    void stopsWhenAnyPageIsRejectedByHis() {
        PhisService phisService = mock(PhisService.class);
        when(phisService.countMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(new PhisResponse<>(true, "1", 101L, null));
        when(phisService.queryMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any()))
                .thenAnswer(invocation -> {
                    MedicalDirectoryQuery query = invocation.getArgument(2);
                    if (query.startRow() == 1) {
                        return new PhisResponse<>(true, "1", entries(1, 100), null);
                    }
                    return new PhisResponse<>(false, "0", null, "HIS拒绝目录查询");
                });
        MedicalDirectoryFetchServiceImpl service = new MedicalDirectoryFetchServiceImpl(
                phisService, new MedicalDirectoryValidationServiceImpl());

        PhisBusinessException error = assertThrows(PhisBusinessException.class,
                () -> service.fetchAll(8L, ParameterEnvironment.TEST, MedicalDirectoryType.CONSUMABLE,
                        RANGE_START, RANGE_END, "ORG-008"));

        assertEquals("HIS拒绝目录查询", error.getMessage());
        verify(phisService, times(2)).queryMedicalDirectory(eq(8L), eq(ParameterEnvironment.TEST), any());
    }

    /**
     * 创建指定编码区间且字段完整的医疗目录来源记录。
     *
     * @param from 目录编码起始数字
     * @param to 目录编码结束数字
     * @return 所有必填字段均完整的合成来源记录
     */
    private List<MedicalDirectoryEntry> entries(int from, int to) {
        return java.util.stream.IntStream.rangeClosed(from, to)
                .mapToObj(number -> new MedicalDirectoryEntry(
                        "C" + number, "耗材" + number, null, "耗材", "个", null, null, null,
                        null, "2026-09-20 00:00:00", null, null, null, null, null, null,
                        null, null, "1"))
                .toList();
    }
}
