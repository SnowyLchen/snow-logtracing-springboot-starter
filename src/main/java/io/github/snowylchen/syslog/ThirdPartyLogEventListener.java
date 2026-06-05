package io.github.snowylchen.syslog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * 第三方 HTTP 调用日志事件监听器
 * 负责捕获所有第三方 HTTP 调用日志事件，并分发给已注册的 Handler，默认开启 @Async 异步处理
 *
 * @author chen
 */
public class ThirdPartyLogEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(ThirdPartyLogEventListener.class);

    private final List<ThirdPartyLogHandler> handlers;

    public ThirdPartyLogEventListener(List<ThirdPartyLogHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * 异步消费第三方 HTTP 调用日志事件
     */
    @Async
    @EventListener
    public void onThirdPartyLog(ThirdPartyLogEvent event) {
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        ThirdPartyLogInfo logInfo = event.getLogInfo();
        for (ThirdPartyLogHandler handler : handlers) {
            try {
                handler.handle(logInfo);
            } catch (Exception e) {
                LOG.warn("[snow-logtracing] ThirdPartyLogHandler 执行异常: {}",
                        handler.getClass().getSimpleName(), e);
            }
        }
    }
}
