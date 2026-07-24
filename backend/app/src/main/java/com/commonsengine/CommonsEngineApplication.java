package com.commonsengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 公地引擎启动入口
 *
 * 主类放在 com.commonsengine 根包下，
 * Spring Boot 默认扫描 com.commonsengine.** 全部子包，
 * 覆盖所有模块的 @Entity、Repository、@Component。
 */
@SpringBootApplication
public class CommonsEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommonsEngineApplication.class, args);
    }
}
