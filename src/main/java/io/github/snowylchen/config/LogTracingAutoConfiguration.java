package io.github.snowylchen.config;

import io.github.snowylchen.aspect.HttpRequestLogAspect;
import io.github.snowylchen.aspect.ServiceAutoTraceAspect;
import io.github.snowylchen.aspect.TraceAnnotationAspect;
import io.github.snowylchen.async.TraceContextTaskDecorator;
import io.github.snowylchen.filter.TraceFilter;
import io.github.snowylchen.interceptor.RestTemplateTraceInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;

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

    // ==================== 追踪功能 ====================

    /**
     * TraceFilter - 请求入口，生成/提取 TraceId，精确计时
     */
    @Bean
    @ConditionalOnProperty(prefix = "snow.logtracing.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TraceFilter> traceFilterRegistration(LogTracingProperties properties) {
        FilterRegistrationBean<TraceFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceFilter(properties));
        registration.addUrlPatterns("/*");
        registration.setName("traceFilter");
        registration.setOrder(Integer.MIN_VALUE + 10);
        return registration;
    }

    /**
     * TraceAnnotationAspect - @Trace 注解切面
     */
    @Bean
    @ConditionalOnProperty(prefix = "snow.logtracing.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TraceAnnotationAspect traceAnnotationAspect() {
        return new TraceAnnotationAspect();
    }

    /**
     * ServiceAutoTraceAspect - Service 层自动追踪切面
     * 通过 snow.logtracing.trace.auto-trace-service=true 开启
     */
    @Bean
    @ConditionalOnProperty(prefix = "snow.logtracing.trace", name = "auto-trace-service", havingValue = "true")
    public ServiceAutoTraceAspect serviceAutoTraceAspect() {
        return new ServiceAutoTraceAspect();
    }

    // ==================== 跨服务传播 ====================

    /**
     * RestTemplate 追踪拦截器
     */
    @Bean
    @ConditionalOnClass(RestTemplate.class)
    @ConditionalOnProperty(prefix = "snow.logtracing.propagation", name = "rest-template", havingValue = "true", matchIfMissing = true)
    public RestTemplateTraceInterceptor restTemplateTraceInterceptor() {
        return new RestTemplateTraceInterceptor();
    }

    /**
     * BeanPostProcessor: 自动为所有 RestTemplate Bean 添加追踪拦截器
     */
    @Bean
    @ConditionalOnClass(RestTemplate.class)
    @ConditionalOnProperty(prefix = "snow.logtracing.propagation", name = "rest-template", havingValue = "true", matchIfMissing = true)
    public BeanPostProcessor restTemplateTraceBeanPostProcessor(RestTemplateTraceInterceptor interceptor) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof RestTemplate) {
                    RestTemplate restTemplate = (RestTemplate) bean;
                    java.util.List interceptors = new ArrayList<>(restTemplate.getInterceptors());
                    // 避免重复添加
                    if (interceptors.stream().noneMatch(i -> i instanceof RestTemplateTraceInterceptor)) {
                        interceptors.add(interceptor);
                        restTemplate.setInterceptors(interceptors);
                    }
                }
                return bean;
            }
        };
    }

    /**
     * Feign 追踪拦截器配置
     * 隔离到独立内部类，避免 classpath 中没有 Feign 时触发 NoClassDefFoundError。
     * @ConditionalOnClass 作用在类级别，Spring 在加载此内部类之前先检查条件，
     * 条件不满足时整个类不会被加载，从而避免 JVM 内省时解析 feign.RequestInterceptor。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    @ConditionalOnProperty(prefix = "snow.logtracing.propagation", name = "feign", havingValue = "true")
    static class FeignTraceAutoConfiguration {

        @Bean
        public io.github.snowylchen.interceptor.FeignTraceInterceptor feignTraceInterceptor() {
            return new io.github.snowylchen.interceptor.FeignTraceInterceptor();
        }
    }

    // ==================== 异步支持 ====================

    /**
     * 追踪上下文任务装饰器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "snow.logtracing.async", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TraceContextTaskDecorator traceContextTaskDecorator() {
        return new TraceContextTaskDecorator();
    }
}
