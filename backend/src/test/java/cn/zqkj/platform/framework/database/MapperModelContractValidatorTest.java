package cn.zqkj.platform.framework.database;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证启动期Mapper和Java模型一致性检查覆盖全部业务目录。 */
class MapperModelContractValidatorTest {

    /** 验证全部生产Mapper构造映射与Java记录组件一致。 */
    @Test
    void validatesEveryProductionMapperResultMap() throws Exception {
        Configuration configuration = new Configuration();
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/**/*Mapper.xml");
        Arrays.sort(resources, java.util.Comparator.comparing(Resource::getFilename));
        for (Resource resource : resources) {
            try (InputStream input = resource.getInputStream()) {
                new XMLMapperBuilder(input, configuration, resource.getDescription(),
                        configuration.getSqlFragments()).parse();
            }
        }

        List<DatabaseContractViolation> violations = new MapperModelContractValidator()
                .validate(configuration);

        assertTrue(violations.isEmpty(), () -> violations.stream()
                .map(DatabaseContractViolation::format)
                .collect(java.util.stream.Collectors.joining(System.lineSeparator())));
    }
}
