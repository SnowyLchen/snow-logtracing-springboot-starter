package io.github.snowylchen.trace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Span 信息，记录一次操作的追踪数据
 *
 * @author chen
 */
public class SpanInfo {

    private final String spanId;
    private final String parentSpanId;
    private final String operationName;
    private final SpanKind kind;
    private final long startNanos;
    private final Map<String, String> tags;
    private final List<SpanInfo> children;

    private long endNanos;
    private boolean error;
    private String errorMessage;

    public SpanInfo(String spanId, String parentSpanId, String operationName, SpanKind kind) {
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.operationName = operationName;
        this.kind = kind;
        this.startNanos = System.nanoTime();
        this.tags = new LinkedHashMap<>();
        this.children = new ArrayList<>();
    }

    /**
     * 结束 Span，记录结束时间
     */
    public void finish() {
        this.endNanos = System.nanoTime();
    }

    /**
     * 标记为异常
     */
    public void markError(String message) {
        this.error = true;
        this.errorMessage = message;
        finish();
    }

    /**
     * 获取精确耗时（毫秒）
     */
    public long getDurationMs() {
        long end = endNanos > 0 ? endNanos : System.nanoTime();
        return (end - startNanos) / 1_000_000;
    }

    public void addTag(String key, String value) {
        tags.put(key, value);
    }

    public void addChild(SpanInfo child) {
        children.add(child);
    }

    public String getSpanId() {
        return spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public String getOperationName() {
        return operationName;
    }

    public SpanKind getKind() {
        return kind;
    }

    public long getStartNanos() {
        return startNanos;
    }

    public long getEndNanos() {
        return endNanos;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public List<SpanInfo> getChildren() {
        return children;
    }

    public boolean isError() {
        return error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
