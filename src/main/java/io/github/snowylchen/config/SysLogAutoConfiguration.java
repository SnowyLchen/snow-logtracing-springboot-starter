package io.github.snowylchen.config;

import io.github.snowylchen.aspect.SysLogAspect;
import io.github.snowylchen.syslog.OperatorProvider;
import io.github.snowylchen.syslog.SysLogEventListener;
import io.github.snowylchen.syslog.SysLogHandler;
import io.github.snowylchen.syslog.ThirdPartyLogEventListener;
import io.github.snowylchen.syslog.ThirdPartyLogHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Collections;
import java.util.List;

/**
 * 系统操作日志模块自动配置
 *
 * @author chen
 */
@AutoConfiguration(after = LogTracingAutoConfiguration.class)
@EnableAsync
@ConditionalOnProperty(prefix = "snow.logtracing", name = "enable", havingValue = "true", matchIfMissing = true)
public class SysLogAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "snow.logtracing.sys-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SysLogAspect sysLogAspect(ApplicationEventPublisher publisher,
                                     LogTracingProperties properties,
                                     @Autowired(required = false) OperatorProvider operatorProvider) {
        return new SysLogAspect(publisher, properties, operatorProvider);
    }

    @Bean
    @ConditionalOnProperty(prefix = "snow.logtracing.sys-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SysLogEventListener sysLogEventListener(
            @Autowired(required = false) List<SysLogHandler> handlers) {
        return new SysLogEventListener(handlers != null ? handlers : Collections.emptyList());
    }

    @Bean
    @ConditionalOnProperty(prefix = "snow.logtracing.third-party", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ThirdPartyLogEventListener thirdPartyLogEventListener(
            @Autowired(required = false) List<ThirdPartyLogHandler> handlers) {
        return new ThirdPartyLogEventListener(handlers != null ? handlers : Collections.emptyList());
    }

    @Bean
    @ConditionalOnProperty(prefix = "snow.logtracing.sys-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public io.github.snowylchen.syslog.SysLogLifecycleListener sysLogLifecycleListener(ApplicationEventPublisher publisher) {
        return new io.github.snowylchen.syslog.SysLogLifecycleListener(publisher);
    }
}
