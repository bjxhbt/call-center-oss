package com.och.ai.asr.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 分页拦截器（方案 7.14 列表分页所需）。
 *
 * <p>仅当容器中尚未存在 {@link MybatisPlusInterceptor} 时注册，避免与其他模块/平台配置冲突。
 * 根据 JDBC 连接识别分页方言，兼容 KingbaseES 与 MySQL。</p>
 */
@Configuration
public class MybatisPlusAsrConfig {

    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor asrMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // Detect the dialect from JDBC so both KingbaseES and MySQL are supported.
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
