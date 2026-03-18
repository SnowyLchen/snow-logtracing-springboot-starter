package io.github.snowylchen.config;

import io.github.snowylchen.aspect.HttpRequestLogAspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 日志追踪自动配置
 *
 * @author chen
 * @date 2024/03/18
 */
@AutoConfiguration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan(basePackages = "io.github.snowylchen")
@ConditionalOnProperty(prefix = "snow.logtracing", name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LogTracingProperties.class)
public class LogTracingAutoConfiguration {

    @Bean
    public HttpRequestLogAspect httpRequestLogAspect(LogTracingProperties logTracingProperties) {
        HttpRequestLogAspect aspect = new HttpRequestLogAspect();
        aspect.setLogTracingProperties(logTracingProperties);
        return aspect;
    }
}
