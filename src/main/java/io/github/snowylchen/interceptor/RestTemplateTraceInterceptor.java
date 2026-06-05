package io.github.snowylchen.interceptor;

import io.github.snowylchen.config.LogTracingProperties;
import io.github.snowylchen.filter.TraceFilter;
import io.github.snowylchen.syslog.ThirdPartyLogEvent;
import io.github.snowylchen.syslog.ThirdPartyLogInfo;
import io.github.snowylchen.trace.SpanInfo;
import io.github.snowylchen.trace.SpanKind;
import io.github.snowylchen.trace.TraceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * RestTemplate 追踪拦截器
 * 自动将 TraceId/SpanId 注入到下游 HTTP 请求头中，
 * 同时创建 CLIENT 类型的子 Span 记录下游调用耗时，并按需记录第三方 HTTP 接口调用日志。
 *
 * @author chen
 */
public class RestTemplateTraceInterceptor implements ClientHttpRequestInterceptor {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RestTemplateTraceInterceptor.class);

    private final ApplicationEventPublisher publisher;
    private final LogTracingProperties properties;

    public RestTemplateTraceInterceptor(ApplicationEventPublisher publisher, LogTracingProperties properties) {
        this.publisher = publisher;
        this.properties = properties;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        TraceContext context = TraceContext.getCurrent();
        if (context == null) {
            return executeWithLog(request, body, execution, null);
        }

        SpanInfo span = null;
        try {
            // 注入 trace header 到下游请求
            request.getHeaders().set(TraceFilter.HEADER_TRACE_ID, context.getTraceId());
            String currentSpanId = TraceContext.currentSpanId();
            if (currentSpanId != null) {
                request.getHeaders().set(TraceFilter.HEADER_SPAN_ID, currentSpanId);
            }

            // 创建 CLIENT 类型子 Span
            String operationName = request.getMethod() + " " + request.getURI().toString();
            span = context.startSpan(operationName, SpanKind.CLIENT);
            span.addTag("http.method", String.valueOf(request.getMethod()));
            span.addTag("http.url", request.getURI().toString());
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] RestTemplate 追踪前置处理异常", e);
        }

        return executeWithLog(request, body, execution, span);
    }

    private ClientHttpResponse executeWithLog(HttpRequest request, byte[] body,
                                              ClientHttpRequestExecution execution,
                                              SpanInfo span) throws IOException {
        long startTime = System.currentTimeMillis();
        ClientHttpResponse response = null;
        IOException executeException = null;

        try {
            response = execution.execute(request, body);
            try {
                if (span != null) {
                    span.addTag("http.status", String.valueOf(response.getStatusCode().value()));
                    TraceContext.getCurrent().finishSpan();
                }
            } catch (Exception e) {
                LOG.debug("[snow-logtracing] RestTemplate 追踪后置处理异常", e);
            }
            return response;
        } catch (IOException e) {
            executeException = e;
            try {
                if (span != null) {
                    span.markError(e.getMessage());
                    TraceContext.getCurrent().finishSpan();
                }
            } catch (Exception ex) {
                LOG.debug("[snow-logtracing] RestTemplate 追踪异常处理失败", ex);
            }
            throw e;
        } finally {
            try {
                if (properties != null && properties.getThirdParty().isEnabled()) {
                    publishThirdPartyLog(request, body, response, executeException, System.currentTimeMillis() - startTime);
                }
            } catch (Exception ex) {
                LOG.debug("[snow-logtracing] RestTemplate 记录第三方调用日志异常", ex);
            }
        }
    }

    private void publishThirdPartyLog(HttpRequest request, byte[] body, ClientHttpResponse response, Exception e, long costTime) {
        try {
            ThirdPartyLogInfo logInfo = new ThirdPartyLogInfo();
            logInfo.setTraceId(TraceContext.currentTraceId());
            logInfo.setRequestUrl(request.getURI().toString());
            logInfo.setRequestMethod(request.getMethod().name());
            logInfo.setRequestHeaders(com.alibaba.fastjson2.JSON.toJSONString(request.getHeaders()));

            if (body != null && body.length > 0) {
                logInfo.setRequestBody(new String(body, StandardCharsets.UTF_8));
            }

            if (response != null) {
                logInfo.setResponseCode(response.getRawStatusCode());
                byte[] respBytes = StreamUtils.copyToByteArray(response.getBody());
                logInfo.setResponseBody(new String(respBytes, StandardCharsets.UTF_8));
                logInfo.setSuccess(response.getStatusCode().is2xxSuccessful());
            } else {
                logInfo.setSuccess(false);
            }

            if (e != null) {
                logInfo.setErrorMessage(e.getMessage());
            }

            logInfo.setCostTime(costTime);
            logInfo.setCreateTime(LocalDateTime.now());

            publisher.publishEvent(new ThirdPartyLogEvent(logInfo));
        } catch (Exception ex) {
            LOG.debug("[snow-logtracing] 构建并发布第三方日志事件失败", ex);
        }
    }
}
