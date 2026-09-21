package cn.zqkj.platform.masterdata.domain.hospitaldirectory.model;

/**
 * 100-003展开行中经校验后的目录关联。
 *
 * <p>持久化目标：{@code dbo.md_hospital_directory_relation}（医院综合目录正式关系表）。</p>
 *
 * <p>业务说明：只保存医生-病区、病区-科室或床位-病区关系；单个目录类型完整校验成功后才会替换旧关系。</p>
 *
 * @param relationType 关联类型：医生-病区、病区-科室或床位-病区
 * @param sourceCode 关联起点的来源编码
 * @param targetCode 关联终点的来源编码
 */
public record HospitalDirectoryRelationRecord(String relationType, String sourceCode, String targetCode) {
}
