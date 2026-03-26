package io.github.snowylchen.trace;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 追踪上下文，基于 ThreadLocal 存储当前线程的追踪信息
 *
 * @author chen
 */
public class TraceContext {

    private static final ThreadLocal<TraceContext> CONTEXT_HOLDER = new ThreadLocal<>();

    private final String traceId;
    private final Deque<SpanInfo> spanStack = new ArrayDeque<>();
    private SpanInfo rootSpan;

    public TraceContext(String traceId) {
        this.traceId = traceId;
    }

    /**
     * 获取当前线程的追踪上下文
     */
    public static TraceContext getCurrent() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 设置当前线程的追踪上下文
     */
    public static void setCurrent(TraceContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 清除当前线程的追踪上下文，防止 ThreadLocal 泄漏
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 获取当前 traceId
     */
    public static String currentTraceId() {
        TraceContext ctx = getCurrent();
        return ctx != null ? ctx.getTraceId() : null;
    }

    /**
     * 获取当前 spanId
     */
    public static String currentSpanId() {
        TraceContext ctx = getCurrent();
        if (ctx != null) {
            SpanInfo span = ctx.peekSpan();
            return span != null ? span.getSpanId() : null;
        }
        return null;
    }

    /**
     * 开始一个新的 Span，压入栈中
     */
    public SpanInfo startSpan(String operationName, SpanKind kind) {
        String parentSpanId = null;
        SpanInfo parentSpan = spanStack.peek();
        if (parentSpan != null) {
            parentSpanId = parentSpan.getSpanId();
        }

        String spanId = TraceIdGenerator.generateSpanId();
        SpanInfo span = new SpanInfo(spanId, parentSpanId, operationName, kind);

        if (rootSpan == null) {
            rootSpan = span;
        }

        // 将子 Span 挂到父 Span 下
        if (parentSpan != null) {
            parentSpan.addChild(span);
        }

        spanStack.push(span);
        return span;
    }

    /**
     * 结束当前 Span，弹出栈
     */
    public SpanInfo finishSpan() {
        SpanInfo span = spanStack.poll();
        if (span != null) {
            span.finish();
        }
        return span;
    }

    /**
     * 查看当前栈顶的 Span（不弹出）
     */
    public SpanInfo peekSpan() {
        return spanStack.peek();
    }

    /**
     * 创建上下文快照，用于异步线程传递
     */
    public TraceContextSnapshot createSnapshot() {
        SpanInfo currentSpan = peekSpan();
        String currentSpanId = currentSpan != null ? currentSpan.getSpanId() : null;
        return new TraceContextSnapshot(traceId, currentSpanId);
    }

    public String getTraceId() {
        return traceId;
    }

    public SpanInfo getRootSpan() {
        return rootSpan;
    }

    /**
     * 追踪上下文快照，用于跨线程传递
     */
    public static class TraceContextSnapshot {
        private final String traceId;
        private final String parentSpanId;

        public TraceContextSnapshot(String traceId, String parentSpanId) {
            this.traceId = traceId;
            this.parentSpanId = parentSpanId;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getParentSpanId() {
            return parentSpanId;
        }
    }
}
