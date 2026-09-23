package cn.zqkj.platform.masterdata.domain.icd10.model;

/** ICD10单一诊断类别同步的可确认处理状态。 */
public enum Icd10SyncResultStatus {

    /** 来源取得、校验和当前目录发布均已完成。 */
    COMPLETED,
    /** 来源通信结果不能确认，未更新当前目录。 */
    RESULT_UNKNOWN,
    /** 来源明确失败、校验失败或本地发布失败，未更新当前目录。 */
    FAILED
}
