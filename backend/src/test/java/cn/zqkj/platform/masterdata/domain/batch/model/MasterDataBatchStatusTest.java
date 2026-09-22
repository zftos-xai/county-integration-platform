package cn.zqkj.platform.masterdata.domain.batch.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证基础数据批次只允许按已确认业务链迁移。
 */
class MasterDataBatchStatusTest {

    /** 验证自动同步运行可以从创建推进到完成。 */
    @Test
    void allowsNormalCompletionFlow() {
        assertTrue(MasterDataBatchStatus.CREATED.canTransitionTo(MasterDataBatchStatus.FETCHING));
        assertTrue(MasterDataBatchStatus.FETCHING.canTransitionTo(MasterDataBatchStatus.COMPLETED));
    }

    /** 验证异常结束的运行不能再直接改变状态。 */
    @Test
    void blocksUnsafeCompletion() {
        assertFalse(MasterDataBatchStatus.FAILED.canTransitionTo(MasterDataBatchStatus.COMPLETED));
        assertFalse(MasterDataBatchStatus.COMPLETED_WITH_UNKNOWN.canTransitionTo(MasterDataBatchStatus.COMPLETED));
    }

    /** 验证核查结果确认全部失败时，可以结束原结果未知批次。 */
    @Test
    void allowsConfirmedFailureAfterUnknownResult() {
        assertTrue(MasterDataBatchStatus.RESULT_UNKNOWN.canTransitionTo(MasterDataBatchStatus.FAILED));
    }

    /** 验证只有四类目录均完成的运行标记为完整完成。 */
    @Test
    void identifiesCompletedState() {
        assertTrue(MasterDataBatchStatus.COMPLETED.isCompleted());
        assertFalse(MasterDataBatchStatus.COMPLETED_WITH_ERRORS.isCompleted());
    }
}
