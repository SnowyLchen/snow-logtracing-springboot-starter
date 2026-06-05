package io.github.snowylchen.config;

import io.github.snowylchen.aspect.ServiceAutoTraceAspect;
import io.github.snowylchen.aspect.TraceAnnotationAspect;
import io.github.snowylchen.async.TraceContextTaskDecorator;
import io.github.snowylchen.interceptor.RestTemplateTraceInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;

/**
 * 分布式追踪模块自动配置
 * <p>
 * 仅在 snow.logtracing.trace.enabled=true 时激活，
 * 负责注册追踪相关的切面、拦截器和异步支持组件。
 *
 * @author chen
 */
@AutoConfiguration(after = LogTracingAutoConfiguration.class)
@ConditionalOnProperty(prefix = "snow.logtracing", name = "enable", havingValue = "true", matchIfMissing = true)
public class TraceAutoConfiguration {

    // ==================== 追踪切面 ====================

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
    @ConditionalOnProperty(prefix = "snow.logtracing.trace.propagation", name = "rest-template", havingValue = "true", matchIfMissing = true)
    public RestTemplateTraceInterceptor restTemplateTraceInterceptor(
            org.springframework.context.ApplicationEventPublisher publisher,
            LogTracingProperties properties) {
        return new RestTemplateTraceInterceptor(publisher, properties);
    }

    /**
     * BeanPostProcessor: 自动将所有 RestTemplate Bean 的 RequestFactory 包装为 BufferingClientHttpRequestFactory
     * 以支持第三方日志模块重复读取 response body。
     */
    @Bean
    @ConditionalOnClass(RestTemplate.class)
    @ConditionalOnProperty(prefix = "snow.logtracing.third-party", name = "enabled", havingValue = "true", matchIfMissing = true)
    public BeanPostProcessor restTemplateBufferingBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof RestTemplate) {
                    RestTemplate restTemplate = (RestTemplate) bean;
                    if (!(restTemplate.getRequestFactory() instanceof org.springframework.http.client.BufferingClientHttpRequestFactory)) {
                        restTemplate.setRequestFactory(new org.springframework.http.client.BufferingClientHttpRequestFactory(restTemplate.getRequestFactory()));
                    }
                }
                return bean;
            }
        };
    }

    /**
     * BeanPostProcessor: 自动为所有 RestTemplate Bean 添加追踪拦截器
     */
    @Bean
    @ConditionalOnClass(RestTemplate.class)
    @ConditionalOnProperty(prefix = "snow.logtracing.trace.propagation", name = "rest-template", havingValue = "true", matchIfMissing = true)
    public BeanPostProcessor restTemplateTraceBeanPostProcessor(RestTemplateTraceInterceptor interceptor) {
        return new BeanPostProcessor() {
            private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TraceAutoConfiguration.class);

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof RestTemplate) {
                    try {
                        RestTemplate restTemplate = (RestTemplate) bean;
                        java.util.List interceptors = new ArrayList<>(restTemplate.getInterceptors());
                        // 避免重复添加
                        if (interceptors.stream().noneMatch(i -> i instanceof RestTemplateTraceInterceptor)) {
                            interceptors.add(interceptor);
                            restTemplate.setInterceptors(interceptors);
                        }
                    } catch (Exception e) {
                        log.debug("[snow-logtracing] 为 RestTemplate [{}] 添加追踪拦截器失败", beanName, e);
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
    @ConditionalOnProperty(prefix = "snow.logtracing.trace.propagation", name = "feign", havingValue = "true")
    static class FeignTraceAutoConfiguration {

        @Bean
        public io.github.snowylchen.interceptor.FeignTraceInterceptor feignTraceInterceptor() {
            return new io.github.snowylchen.interceptor.FeignTraceInterceptor();
        }

        @Bean
        @ConditionalOnProperty(prefix = "snow.logtracing.third-party", name = "enabled", havingValue = "true", matchIfMissing = true)
        public io.github.snowylchen.interceptor.FeignThirdPartyLogger feignThirdPartyLogger(
                org.springframework.context.ApplicationEventPublisher publisher) {
            return new io.github.snowylchen.interceptor.FeignThirdPartyLogger(publisher);
        }
    }

    // ==================== 异步支持 ====================

    /**
     * 追踪上下文任务装饰器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "snow.logtracing.trace.async", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TraceContextTaskDecorator traceContextTaskDecorator() {
        return new TraceContextTaskDecorator();
    }
}