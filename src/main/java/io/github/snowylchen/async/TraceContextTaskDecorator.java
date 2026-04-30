package io.github.snowylchen.async;

import io.github.snowylchen.trace.TraceContext;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 追踪上下文任务装饰器
 * 在父线程捕获 TraceContext 快照和 MDC 上下文，在子线程中恢复，子线程结束后清理
 * <p>
 * 使用方式：配置到 ThreadPoolTaskExecutor 的 taskDecorator 中
 * <pre>
 * {@code
 * @Bean
 * public ThreadPoolTaskExecutor taskExecutor(TraceContextTaskDecorator decorator) {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setTaskDecorator(decorator);
 *     return executor;
 * }
 * }
 * </pre>
 *
 * @author chen
 */
public class TraceContextTaskDecorator implements TaskDecorator {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TraceContextTaskDecorator.class);

    @Override
    public Runnable decorate(Runnable runnable) {
        // 在父线程中捕获上下文
        TraceContext parentContext = null;
        Map<String, String> parentMdc = null;
        try {
            parentContext = TraceContext.getCurrent();
            parentMdc = MDC.getCopyOfContextMap();
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] 捕获父线程上下文异常", e);
        }

        final TraceContext capturedContext = parentContext;
        final Map<String, String> capturedMdc = parentMdc;

        return () -> {
            try {
                // 在子线程中恢复上下文
                try {
                    if (capturedContext != null) {
                        TraceContext childContext = new TraceContext(capturedContext.getTraceId());
                        TraceContext.setCurrent(childContext);
                    }
                    if (capturedMdc != null) {
                        MDC.setContextMap(capturedMdc);
                    }
                } catch (Exception e) {
                    LOG.debug("[snow-logtracing] 子线程恢复上下文异常", e);
                }

                runnable.run();
            } finally {
                // 子线程结束后清理，防止 ThreadLocal 泄漏
                try {
                    TraceContext.clear();
                    MDC.clear();
                } catch (Exception e) {
                    LOG.debug("[snow-logtracing] 子线程清理上下文异常", e);
                }
            }
        };
    }
}
