package cn.zqkj;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("cn.zqkj.exchange.mapper")
@SpringBootApplication
public class IntegrationPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationPlatformApplication.class, args);
    }
}
