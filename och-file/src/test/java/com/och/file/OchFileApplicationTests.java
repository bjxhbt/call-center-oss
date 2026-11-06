package com.och.file;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * och-file 是被依赖的库模块，不提供独立可启动的 Application（无 @SpringBootApplication），
 * 因此 @SpringBootTest 无法找到 SpringBootConfiguration，会阻断全量 Maven Reactor 测试。
 *
 * 方案一「构建基线收口」(计划 9.2 P0)：隔离该自动生成的 contextLoads 冒烟测试，
 * 使 Reactor 测试可继续执行；och-file 的真实能力由 och-api 聚合后的集成测试覆盖。
 */
@Disabled("och-file 为库模块无独立 Application，contextLoads 无意义；改由 och-api 聚合验证")
@SpringBootTest
class OchFileApplicationTests {

    @Test
    void contextLoads() {
    }

}
