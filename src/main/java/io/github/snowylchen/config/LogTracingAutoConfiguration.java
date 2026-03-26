package io.github.snowylchen.config;

import io.github.snowylchen.aspect.HttpRequestLogAspect;
import io.github.snowylchen.filter.TraceFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 日志追踪自动配置（公共层）
 * <p>
 * 负责注册公共组件和 TraceFilter。
 * 追踪模块专属 bean 由 {@link TraceAutoConfiguration} 管理。
 *
 * @author chen
 * @date 2024/03/18
 */
@AutoConfiguration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ConditionalOnProperty(prefix = "snow.logtracing", name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LogTracingProperties.class)
public class LogTracingAutoConfiguration {

    /**
     * HttpRequestLogAspect - Controller 层请求/响应日志
     * 受 snow.logtracing.request-log.enabled 控制
     */
    @Bean
    @ConditionalOnProperty(prefix = "snow.logtracing.request-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HttpRequestLogAspect httpRequestLogAspect(LogTracingProperties logTracingProperties) {
        HttpRequestLogAspect aspect = new HttpRequestLogAspect();
        aspect.setLogTracingProperties(logTracingProperties);
        return aspect;
    }

    /**
     * TraceFilter - 统一的请求观测过滤器
     * <p>
     * 内部根据 timing.enabled 和 trace.enabled 适配不同运行模式。
     * 当 timing 和 trace 都关闭时，Filter 内部为 no-op（直接放行）。
     */
    @Bean
    public FilterRegistrationBean<TraceFilter> traceFilterRegistration(LogTracingProperties properties) {
        FilterRegistrationBean<TraceFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceFilter(properties));
        registration.addUrlPatterns("/*");
        registration.setName("traceFilter");
        registration.setOrder(Integer.MIN_VALUE + 10);
        return registration;
    }
}
