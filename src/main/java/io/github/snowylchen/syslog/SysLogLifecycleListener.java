package io.github.snowylchen.syslog;

import cn.hutool.core.util.IdUtil;
import io.github.snowylchen.annotation.OperationType;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

import java.time.LocalDateTime;

/**
 * 监听应用程序生命周期事件并记录日志
 *
 * @author chen
 */
public class SysLogLifecycleListener implements ApplicationListener<org.springframework.context.ApplicationEvent> {

    private final ApplicationEventPublisher publisher;

    public SysLogLifecycleListener(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void onApplicationEvent(org.springframework.context.ApplicationEvent event) {
        if (event instanceof ApplicationReadyEvent) {
            publishLifecycleLog("应用程序启动", OperationType.STARTUP);
        } else if (event instanceof ContextClosedEvent) {
            publishLifecycleLog("应用程序关闭", OperationType.SHUTDOWN);
        }
    }

    private void publishLifecycleLog(String description, String type) {
        SysLogInfo logInfo = new SysLogInfo();
        logInfo.setLogId(IdUtil.fastSimpleUUID());
        logInfo.setOperateTime(LocalDateTime.now());
        logInfo.setDescription(description);
        logInfo.setOperationType(type);
        logInfo.setModule("系统运行");
        logInfo.setSuccess(true);
        logInfo.setOperatorName("System");
        logInfo.setOperatorId("0");

        // 关键：由于启动/关闭不是通过 HTTP 请求触发，不需要填充 RequestInfo 和 TraceId

        publisher.publishEvent(new SysLogEvent(logInfo));
    }
}
