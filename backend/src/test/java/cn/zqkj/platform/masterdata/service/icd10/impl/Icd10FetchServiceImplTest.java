package cn.zqkj.platform.masterdata.service.icd10.impl;

import cn.zqkj.platform.his.domain.icd10.dto.Icd10Query;
import cn.zqkj.platform.his.domain.icd10.model.Icd10Entry;
import cn.zqkj.platform.his.domain.protocol.model.PhisResponse;
import cn.zqkj.platform.his.exception.PhisProtocolException;
import cn.zqkj.platform.his.service.PhisService;
import cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10HisInvocation;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10SourceRecord;
import cn.zqkj.platform.masterdata.service.icd10.Icd10ValidationService;
import cn.zqkj.platform.masterdata.mapper.icd10.Icd10SyncMapper;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 核对100-006结束行不包含时的完整分页和缺页拒绝。 */
class Icd10FetchServiceImplTest {

    /** 101条数据应请求1-101及101-102，最后一条不能遗漏。 */
    @Test
    void fetchesExclusiveEndPagesThroughDeclaredCount() {
        PhisService his = mock(PhisService.class);
        Icd10ValidationService validation = mock(Icd10ValidationService.class);
        Icd10SyncMapper mapper = invocationMapper();
        MasterDataBatchSnapshot batch = batch();
        when(his.countIcd10(eq(8L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(PhisResponse.success("1", 101L));
        when(his.queryIcd10(eq(8L), eq(ParameterEnvironment.TEST), any())).thenAnswer(call -> {
            Icd10Query query = call.getArgument(2);
            return PhisResponse.success("1", entries((int) query.startRow(), (int) query.endRow()));
        });

        new Icd10FetchServiceImpl(his, validation, mapper, "Asia/Shanghai")
                .fetchAll(batch, 8L, cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DiagnosisCategory.WESTERN);

        ArgumentCaptor<List<Icd10SourceRecord>> records = ArgumentCaptor.forClass(List.class);
        verify(validation).validate(eq(101L), records.capture());
        assertEquals(101, records.getValue().size());
        verify(his).queryIcd10(eq(8L), eq(ParameterEnvironment.TEST),
                org.mockito.ArgumentMatchers.argThat(query -> query.startRow() == 1 && query.endRow() == 101));
        verify(his).queryIcd10(eq(8L), eq(ParameterEnvironment.TEST),
                org.mockito.ArgumentMatchers.argThat(query -> query.startRow() == 101 && query.endRow() == 102));
        verify(mapper, org.mockito.Mockito.times(3)).insertHisInvocation(any(Icd10HisInvocation.class));
    }

    /** 来源少返回一条时必须停止，不能把不完整目录交给校验和发布。 */
    @Test
    void rejectsIncompletePage() {
        PhisService his = mock(PhisService.class);
        Icd10ValidationService validation = mock(Icd10ValidationService.class);
        Icd10SyncMapper mapper = invocationMapper();
        when(his.countIcd10(eq(8L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(PhisResponse.success("1", 100L));
        when(his.queryIcd10(eq(8L), eq(ParameterEnvironment.TEST), any()))
                .thenReturn(PhisResponse.success("1", entries(1, 100)));

        assertThrows(PhisProtocolException.class, () -> new Icd10FetchServiceImpl(his, validation, mapper, "Asia/Shanghai")
                .fetchAll(batch(), 8L, cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DiagnosisCategory.WESTERN));
    }

    /** 提供无需机构归属的公共诊断批次及固定查询范围。 */
    private MasterDataBatchSnapshot batch() {
        MasterDataBatchSnapshot batch = mock(MasterDataBatchSnapshot.class);
        when(batch.environment()).thenReturn(ParameterEnvironment.TEST);
        when(batch.rangeStart()).thenReturn(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(batch.rangeEnd()).thenReturn(LocalDateTime.of(2026, 9, 22, 0, 0));
        return batch;
    }

    /** 提供正常写入一次调用事实的追踪表映射器替身。 */
    private Icd10SyncMapper invocationMapper() {
        Icd10SyncMapper mapper = mock(Icd10SyncMapper.class);
        when(mapper.insertHisInvocation(any(Icd10HisInvocation.class))).thenReturn(1);
        return mapper;
    }

    /** 生成与请求行区间长度相同的完整协议数据。 */
    private List<Icd10Entry> entries(int from, int toExclusive) {
        return IntStream.range(from, toExclusive).mapToObj(number ->
                new Icd10Entry("D" + number, "病种" + number, null, null, "2026-01-01 00:00:00", null)).toList();
    }
}
