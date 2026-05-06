package io.github.snowylchen.syslog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * 系统日志事件监听器，异步分发给各个 handler
 *
 * @author chen
 */
public class SysLogEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(SysLogEventListener.class);

    private final List<SysLogHandler> handlers;

    public SysLogEventListener(List<SysLogHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * 异步消费日志事件
     */
    @Async
    @EventListener
    public void onSysLog(SysLogEvent event) {
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        SysLogInfo logInfo = event.getSysLogInfo();
        for (SysLogHandler handler : handlers) {
            try {
                handler.handle(logInfo);
            } catch (Exception e) {
                // 单个 handler 失败不影响其他 handler
                LOG.warn("[snow-logtracing] SysLogHandler 执行异常: {}",
                        handler.getClass().getSimpleName(), e);
            }
        }
    }
}
