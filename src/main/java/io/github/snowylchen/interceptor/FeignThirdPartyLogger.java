package io.github.snowylchen.interceptor;

import io.github.snowylchen.syslog.ThirdPartyLogEvent;
import io.github.snowylchen.syslog.ThirdPartyLogInfo;
import io.github.snowylchen.trace.TraceContext;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Feign 第三方 HTTP 调用日志记录器
 * 继承自 Feign 的 Logger，在不破坏 Feign 执行流的前提下，捕获请求及响应明细并异步发布事件
 *
 * @author chen
 */
public class FeignThirdPartyLogger extends feign.Logger {

    private final ApplicationEventPublisher publisher;

    public FeignThirdPartyLogger(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    protected void logRequest(String configKey, Level logLevel, feign.Request request) {
        // 请求前置不记录，待响应返回或抛出异常时统一组装记录
    }

    @Override
    protected feign.Response logAndRebufferResponse(String configKey, Level logLevel,
                                                    feign.Response response, long elapsedTime) throws IOException {
        ThirdPartyLogInfo logInfo = new ThirdPartyLogInfo();
        logInfo.setTraceId(TraceContext.currentTraceId());
        logInfo.setRequestUrl(response.request().url());
        logInfo.setRequestMethod(response.request().httpMethod().name());
        logInfo.setRequestHeaders(com.alibaba.fastjson2.JSON.toJSONString(response.request().headers()));

        byte[] requestBody = response.request().body();
        if (requestBody != null && requestBody.length > 0) {
            logInfo.setRequestBody(new String(requestBody, StandardCharsets.UTF_8));
        }

        logInfo.setResponseCode(response.status());
        logInfo.setSuccess(response.status() >= 200 && response.status() < 300);
        logInfo.setCostTime(elapsedTime);
        logInfo.setCreateTime(LocalDateTime.now());

        byte[] responseBodyBytes = null;
        feign.Response rebufferedResponse = response;
        if (response.body() != null) {
            responseBodyBytes = feign.Util.toByteArray(response.body().asInputStream());
            logInfo.setResponseBody(new String(responseBodyBytes, StandardCharsets.UTF_8));
            // 重新包装响应流，避免原消费者读取到空流
            rebufferedResponse = response.toBuilder().body(responseBodyBytes).build();
        }

        try {
            publisher.publishEvent(new ThirdPartyLogEvent(logInfo));
        } catch (Exception e) {
            // 忽略事件发布异常，防止影响主业务流程
        }

        return rebufferedResponse;
    }

    @Override
    protected void log(String configKey, String format, Object... args) {
        // 重写为空，避免多余的控制台日志输出
    }
}
