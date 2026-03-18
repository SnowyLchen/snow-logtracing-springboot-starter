package com.snow.logtracing.config;

import com.snow.logtracing.aspect.HttpRequestLogAspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 日志追踪自动配置
 *
 * @author chen
 * @date 2024/03/18
 */
@Configuration
@EnableAspectJAutoProxy
@ConditionalOnProperty(prefix = "snow.logtracing", name = "enable", havingValue = "true", matchIfMissing = true)
public class LogTracingAutoConfiguration {

    @Bean
    public LogTracingProperties logTracingProperties() {
        return new LogTracingProperties();
    }

    @Bean
    @Autowired(required = false)
    public HttpRequestLogAspect httpRequestLogAspect(LogTracingProperties logTracingProperties) {
        HttpRequestLogAspect aspect = new HttpRequestLogAspect();
        aspect.setLogTracingProperties(logTracingProperties);
        return aspect;
    }
}
