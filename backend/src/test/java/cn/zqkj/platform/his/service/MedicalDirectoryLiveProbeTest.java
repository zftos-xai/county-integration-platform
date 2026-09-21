package cn.zqkj.platform.his.service;

import cn.zqkj.platform.his.domain.dto.MedicalDirectoryCountQuery;
import cn.zqkj.platform.his.domain.dto.MedicalDirectoryQuery;
import cn.zqkj.platform.his.domain.model.MedicalDirectoryEntry;
import cn.zqkj.platform.his.domain.model.MedicalDirectoryType;
import cn.zqkj.platform.his.domain.model.PhisResponse;
import cn.zqkj.platform.masterdata.mapper.MasterDataBatchMapper;
import cn.zqkj.platform.masterdata.domain.model.SourcePagination;
import cn.zqkj.platform.masterdata.domain.model.SourcePage;
import cn.zqkj.platform.masterdata.domain.model.MedicalDirectorySourceRecord;
import cn.zqkj.platform.masterdata.service.MedicalDirectoryValidationService;
import cn.zqkj.platform.system.domain.model.ParameterEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用本机已保存机构配置执行只读100-005联调探测。
 *
 * <p>默认测试套件不会执行外部调用；只有显式提供{@code -DliveHisProbe=true}时才运行。
 * 探测只输出四类声明行数，不输出服务地址、凭证或目录正文。</p>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "liveHisProbe", matches = "true")
class MedicalDirectoryLiveProbeTest {

    private static final String ORGANIZATION_CODE = "8BCDCA4E11A44A9D888D7E70DD4FF44F";
    private final PhisService phisService;
    private final MasterDataBatchMapper batchMapper;
    private final MedicalDirectoryValidationService validationService;

    /**
     * 读取已登记目录类型的真实声明数量，供显式联调测试使用。
     *
     * @param phisService HIS业务服务
     * @param batchMapper 机构主键读取边界
     * @param validationService 目录批次自动校验服务
     */
    @Autowired
    MedicalDirectoryLiveProbeTest(
            PhisService phisService,
            MasterDataBatchMapper batchMapper,
            MedicalDirectoryValidationService validationService
    ) {
        this.phisService = phisService;
        this.batchMapper = batchMapper;
        this.validationService = validationService;
    }

    /** 验证测试机构四类100-005调用均返回明确且非负的声明行数。 */
    @Test
    void readsDeclaredCountsForRegisteredDirectoryTypes() {
        Long organizationId = batchMapper.findEnabledOrganizationId(ORGANIZATION_CODE);
        assertNotNull(organizationId);
        for (MedicalDirectoryType type : MedicalDirectoryType.values()) {
            PhisResponse<Long> response = phisService.countMedicalDirectory(
                    organizationId,
                    ParameterEnvironment.TEST,
                    new MedicalDirectoryCountQuery(
                            type,
                            null,
                            LocalDateTime.of(2000, 1, 1, 0, 0),
                            LocalDateTime.of(2099, 12, 31, 23, 59, 59),
                            ORGANIZATION_CODE
                    )
            );
            assertTrue(response.success(), type.displayName() + "目录行数查询失败：" + response.errorMessage());
            assertNotNull(response.data());
            assertTrue(response.data() >= 0, type.displayName() + "目录行数不能为负数");
            System.out.println("100-005 " + type.displayName() + "声明行数=" + response.data());

            PhisResponse<List<MedicalDirectoryEntry>> page = phisService.queryMedicalDirectory(
                    organizationId,
                    ParameterEnvironment.TEST,
                    new MedicalDirectoryQuery(
                            type,
                            null,
                            1,
                            Math.min(100, response.data()),
                            LocalDateTime.of(2000, 1, 1, 0, 0),
                            LocalDateTime.of(2099, 12, 31, 23, 59, 59),
                            ORGANIZATION_CODE
                    )
            );
            assertTrue(page.success(), type.displayName() + "目录第一页查询失败：" + page.errorMessage());
            assertNotNull(page.data());
            long missingRequired = page.data().stream()
                    .filter(item -> item.directoryCode() == null || item.directoryCode().isBlank()
                            || item.directoryName() == null || item.directoryName().isBlank()
                            || item.categoryName() == null || item.categoryName().isBlank()
                            || item.sourceCreatedAt() == null || item.sourceCreatedAt().isBlank())
                    .count();
            System.out.println("100-004 " + type.displayName() + "第一页返回=" + page.data().size()
                    + "，必填缺失=" + missingRequired);
        }
    }

    /** 验证声明数较小的耗材目录可被全部分页读取，且稳定编码不重复、不缺页。 */
    @Test
    void readsEveryConsumablePageAndMatchesDeclaredCount() {
        Long organizationId = batchMapper.findEnabledOrganizationId(ORGANIZATION_CODE);
        assertNotNull(organizationId);
        LocalDateTime rangeStart = LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime rangeEnd = LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        PhisResponse<Long> count = phisService.countMedicalDirectory(organizationId, ParameterEnvironment.TEST,
                new MedicalDirectoryCountQuery(MedicalDirectoryType.CONSUMABLE, null,
                        rangeStart, rangeEnd, ORGANIZATION_CODE));
        assertTrue(count.success());
        Set<String> stableCodes = new HashSet<>();
        List<MedicalDirectorySourceRecord> records = new ArrayList<>();
        long returned = 0;
        for (SourcePage page : SourcePagination.plan(count.data(), 100)) {
            PhisResponse<List<MedicalDirectoryEntry>> response = phisService.queryMedicalDirectory(
                    organizationId, ParameterEnvironment.TEST,
                    new MedicalDirectoryQuery(MedicalDirectoryType.CONSUMABLE, null,
                            page.startRow(), page.endRow(), rangeStart, rangeEnd, ORGANIZATION_CODE));
            assertTrue(response.success(), "耗材第" + page.startRow() + "至" + page.endRow() + "行查询失败");
            returned += response.data().size();
            response.data().forEach(entry -> {
                assertTrue(stableCodes.add(entry.directoryCode()), "耗材目录出现重复编码：" + entry.directoryCode());
                records.add(new MedicalDirectorySourceRecord(MedicalDirectoryType.CONSUMABLE, entry));
            });
        }
        assertEquals(count.data().longValue(), returned, "耗材分页实际取得数必须等于来源声明数");
        assertEquals(returned, stableCodes.size(), "耗材来源稳定编码数必须等于实际取得数");
        var validation = validationService.validate(count.data(), records);
        assertTrue(validation.valid(), validation.failureSummary());
        System.out.println("100-004 耗材完整分页返回=" + returned + "，声明行数=" + count.data());
    }
}
