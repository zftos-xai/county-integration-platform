package cn.zqkj.platform.system.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证配置主版本同时交付代码注册参数值和已确认的平台系统字典。 */
class ConfigurationDefaultsMigrationTest {

    /** 验证V0400包含当前批准的最小默认配置，防止代码定义与新库初始化结果漂移。 */
    @Test
    void seedsApprovedConfigurationDefaults() throws IOException {
        String migration;
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V0400__configuration_main.sql")) {
            if (stream == null) {
                throw new IOException("找不到V0400配置主版本资源");
            }
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.contains("management.list.default-page-size"));
        assertTrue(migration.contains("management.audit.default-query-limit"));
        assertTrue(migration.contains("EXCHANGE_DIRECTION"));
        assertTrue(migration.contains("EXCHANGE_RESULT"));
        assertTrue(migration.contains("ALERT_LEVEL"));
        assertTrue(migration.contains("N'SYSTEM', N'SYSTEM'"));
    }
}
