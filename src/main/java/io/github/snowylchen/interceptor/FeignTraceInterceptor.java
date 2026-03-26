package io.github.snowylchen.interceptor;

import io.github.snowylchen.filter.TraceFilter;
import io.github.snowylchen.trace.SpanInfo;
import io.github.snowylchen.trace.SpanKind;
import io.github.snowylchen.trace.TraceContext;

/**
 * Feign 追踪拦截器
 * 自动将 TraceId/SpanId 注入到 Feign 请求头中
 * <p>
 * 通过 @ConditionalOnClass 按需加载，仅在 classpath 中存在 Feign 时生效
 *
 * @author chen
 */
public class FeignTraceInterceptor implements feign.RequestInterceptor {

    @Override
    public void apply(feign.RequestTemplate template) {
        TraceContext context = TraceContext.getCurrent();
        if (context == null) {
            return;
        }

        // 注入 trace header
        template.header(TraceFilter.HEADER_TRACE_ID, context.getTraceId());
        String currentSpanId = TraceContext.currentSpanId();
        if (currentSpanId != null) {
            template.header(TraceFilter.HEADER_SPAN_ID, currentSpanId);
        }

        // 创建 CLIENT 类型子 Span
        String operationName = template.method() + " " + template.url();
        SpanInfo span = context.startSpan(operationName, SpanKind.CLIENT);
        span.addTag("http.method", template.method());
        span.addTag("feign.url", template.url());
    }
}
