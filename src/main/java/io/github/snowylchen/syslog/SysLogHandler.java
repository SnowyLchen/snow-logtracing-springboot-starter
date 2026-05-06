package io.github.snowylchen.syslog;

/**
 * 系统日志处理器 SPI
 * <p>
 * 使用者实现此接口并注册为 Spring Bean，即可接收所有 @SysLog 产生的操作日志。
 * 此操作由异步线程触发，不会阻塞业务主流程。
 * </p>
 *
 * @author chen
 */
public interface SysLogHandler {

    /**
     * 处理一条操作日志
     *
     * @param logInfo 日志信息
     */
    void handle(SysLogInfo logInfo);
}
