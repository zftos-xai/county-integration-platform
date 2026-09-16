package cn.zqkj.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 县人民医院与基层卫生院中间接口平台的应用启动入口。
 *
 * <p>组件扫描范围限定在 {@code cn.zqkj.platform}，各 MyBatis Mapper 通过
 * {@code @Mapper} 显式注册。</p>
 */
@SpringBootApplication
public class PlatformApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args JVM 传入的启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
