package io.github.snowylchen.interceptor;

import io.github.snowylchen.filter.TraceFilter;
import io.github.snowylchen.trace.SpanInfo;
import io.github.snowylchen.trace.SpanKind;
import io.github.snowylchen.trace.TraceContext;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * RestTemplate 追踪拦截器
 * 自动将 TraceId/SpanId 注入到下游 HTTP 请求头中，
 * 同时创建 CLIENT 类型的子 Span 记录下游调用耗时
 *
 * @author chen
 */
public class RestTemplateTraceInterceptor implements ClientHttpRequestInterceptor {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RestTemplateTraceInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        TraceContext context = TraceContext.getCurrent();
        if (context == null) {
            return execution.execute(request, body);
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

        try {
            ClientHttpResponse response = execution.execute(request, body);
            try {
                if (span != null) {
                    span.addTag("http.status", String.valueOf(response.getStatusCode().value()));
                    context.finishSpan();
                }
            } catch (Exception e) {
                LOG.debug("[snow-logtracing] RestTemplate 追踪后置处理异常", e);
            }
            return response;
        } catch (IOException e) {
            try {
                if (span != null) {
                    span.markError(e.getMessage());
                    context.finishSpan();
                }
            } catch (Exception ex) {
                LOG.debug("[snow-logtracing] RestTemplate 追踪异常处理失败", ex);
            }
            throw e;
        }
    }
}
