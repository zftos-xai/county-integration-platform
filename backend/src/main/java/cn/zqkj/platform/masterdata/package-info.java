/**
 * 平台基础数据域。
 *
 * <p>本域只负责将已获准来源取得的基础目录转化为平台当前数据，以及记录同步批次的
 * 范围、状态和可复核结果；不负责HIS SOAP协议、机构接口配置或消费方读取。</p>
 *
 * <p>{@code batch} 负责同步批次与来源可用性，{@code hospitaldirectory} 负责100-003医院综合目录，
 * {@code medicaldirectory} 负责100-004/100-005药品、诊疗和耗材目录。</p>
 */
package cn.zqkj.platform.masterdata;
