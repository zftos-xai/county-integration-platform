package cn.zqkj.platform.databasecontract.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 正常维护页面的一次实时数据库契约扫描结果。
 *
 * @param scannedAt 扫描UTC时间
 * @param expectedColumnCount 预期字段数
 * @param issueCount 差异数
 * @param executableCount 可执行数
 * @param issues 差异列表
 */
public record DatabaseContractInspectionVO(
        LocalDateTime scannedAt, int expectedColumnCount, int issueCount,
        int executableCount, List<DatabaseContractIssueVO> issues) {
}
