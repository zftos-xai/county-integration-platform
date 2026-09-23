package cn.zqkj.platform.masterdata.domain.batch.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证基础数据类别与已确认HIS交易保持一致。
 */
class MasterDataCategoryTest {

    /** 验证医院综合目录作为一个完整业务使用100-003交易。 */
    @Test
    void mapsHospitalDirectoryCategoriesToDocumentedTrade() {
        assertEquals("100-003", MasterDataCategory.HOSPITAL_DIRECTORY.dataTradeCode());
        assertEquals(MasterDataScopeType.ORGANIZATION, MasterDataCategory.HOSPITAL_DIRECTORY.scopeType());
    }

    /** 验证来源不提供数量交易时不会伪造声明数量接口。 */
    @Test
    void leavesUnsupportedCountTradeEmpty() {
        assertNull(MasterDataCategory.HOSPITAL_DIRECTORY.countTradeCode());
    }

    /** 验证ICD10明确使用平台公共范围及100-006、100-007交易。 */
    @Test
    void mapsPublicIcd10CategoryToDocumentedTrades() {
        assertEquals("100-006", MasterDataCategory.ICD10_DIAGNOSIS.dataTradeCode());
        assertEquals("100-007", MasterDataCategory.ICD10_DIAGNOSIS.countTradeCode());
        assertEquals(MasterDataScopeType.PLATFORM, MasterDataCategory.ICD10_DIAGNOSIS.scopeType());
    }
}
