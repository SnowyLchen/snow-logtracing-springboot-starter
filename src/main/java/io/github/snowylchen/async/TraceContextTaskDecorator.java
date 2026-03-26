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

    @Override
    public Runnable decorate(Runnable runnable) {
        // 在父线程中捕获上下文
        TraceContext parentContext = TraceContext.getCurrent();
        Map<String, String> parentMdc = MDC.getCopyOfContextMap();

        return () -> {
            try {
                // 在子线程中恢复上下文
                if (parentContext != null) {
                    // 创建子线程的上下文，共享 traceId
                    TraceContext childContext = new TraceContext(parentContext.getTraceId());
                    TraceContext.setCurrent(childContext);
                }
                if (parentMdc != null) {
                    MDC.setContextMap(parentMdc);
                }

                runnable.run();
            } finally {
                // 子线程结束后清理，防止 ThreadLocal 泄漏
                TraceContext.clear();
                MDC.clear();
            }
        };
    }
}
