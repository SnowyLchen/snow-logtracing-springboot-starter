package io.github.snowylchen.syslog;

import org.springframework.context.ApplicationEvent;

/**
 * 第三方 HTTP 调用日志事件
 * 继承自 Spring ApplicationEvent，解耦日志生成与具体存储介质
 *
 * @author chen
 */
public class ThirdPartyLogEvent extends ApplicationEvent {

    private final ThirdPartyLogInfo logInfo;

    public ThirdPartyLogEvent(ThirdPartyLogInfo logInfo) {
        super(logInfo);
        this.logInfo = logInfo;
    }

    public ThirdPartyLogInfo getLogInfo() {
        return logInfo;
    }
}
