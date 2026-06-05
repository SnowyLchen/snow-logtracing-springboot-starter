package io.github.snowylchen.syslog;

/**
 * 第三方日志自定义处理器接口
 * 供主应用实现具体的存储逻辑（如写入 MySQL、Elasticsearch 或 MQ）
 *
 * @author chen
 */
public interface ThirdPartyLogHandler {

    /**
     * 处理第三方 HTTP 调用日志
     *
     * @param logInfo 日志信息明细
     */
    void handle(ThirdPartyLogInfo logInfo);
}
