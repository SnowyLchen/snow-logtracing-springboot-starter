package io.github.snowylchen.syslog;

import org.springframework.context.ApplicationEvent;

/**
 * 系统日志事件
 *
 * @author chen
 */
public class SysLogEvent extends ApplicationEvent {

    private final SysLogInfo sysLogInfo;

    public SysLogEvent(SysLogInfo sysLogInfo) {
        super(sysLogInfo);
        this.sysLogInfo = sysLogInfo;
    }

    public SysLogInfo getSysLogInfo() {
        return sysLogInfo;
    }
}
