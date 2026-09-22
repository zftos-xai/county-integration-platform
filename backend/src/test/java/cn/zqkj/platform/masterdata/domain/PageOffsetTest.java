package cn.zqkj.platform.masterdata.domain;

import cn.zqkj.platform.masterdata.domain.batch.dto.MasterDataBatchQuery;
import cn.zqkj.platform.masterdata.domain.hospitaldirectory.dto.HospitalDirectoryQuery;
import cn.zqkj.platform.masterdata.domain.medicaldirectory.dto.MedicalDirectoryQuery;
import jakarta.validation.Validation;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证三个管理分页查询在合法的极大页码下不会发生整数溢出。 */
class PageOffsetTest {

    /** 极大页码与最大页大小相乘后仍应得到正确的长整型偏移量。 */
    @Test
    void calculatesLargeOffsetsWithoutOverflow() {
        long expected = 214_748_364_600L;
        assertEquals(expected, new MedicalDirectoryQuery(null, null, null, Integer.MAX_VALUE, 100).offset());
        assertEquals(expected, new HospitalDirectoryQuery(null, null, null, Integer.MAX_VALUE, 100).offset());
        assertEquals(expected, new MasterDataBatchQuery(
                null, null, null, null, null, null, Integer.MAX_VALUE, 100).offset());
    }

    /** 统一查询对象的分页和时间约束在进入用例前执行。 */
    @Test
    void rejectsInvalidBatchQueryBeforeMapperInvocation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            Assertions.assertFalse(validator.validate(
                    new MasterDataBatchQuery(null, null, null, null, null, null, 0, 20)).isEmpty());
            Assertions.assertFalse(validator.validate(
                    new MasterDataBatchQuery(null, null, null, null, null, null, 1, 101)).isEmpty());
            Assertions.assertFalse(validator.validate(
                    new MasterDataBatchQuery(null, null, null, null,
                            OffsetDateTime.parse("2026-09-22T00:00:00Z"),
                            OffsetDateTime.parse("2026-09-21T00:00:00Z"), 1, 20)).isEmpty());
        }
    }
}
