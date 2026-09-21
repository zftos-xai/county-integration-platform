package cn.zqkj.platform.masterdata.domain.batch.model;

/**
 * 定义基础数据批次的业务归属范围。
 */
public enum MasterDataScopeType {

    /** 数据只属于一个已经映射的平台机构。 */
    ORGANIZATION,

    /** 数据属于平台公共目录，不按机构重复保存。 */
    PLATFORM
}
