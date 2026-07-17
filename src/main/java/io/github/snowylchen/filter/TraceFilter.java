package io.github.snowylchen.filter;

import io.github.snowylchen.config.LogTracingProperties;
import io.github.snowylchen.trace.SpanInfo;
import io.github.snowylchen.trace.SpanKind;
import io.github.snowylchen.trace.TraceContext;
import io.github.snowylchen.trace.TraceIdGenerator;

import static io.github.snowylchen.util.LogUtil.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 请求观测过滤器，根据配置独立或协同提供两大能力：
 * <ul>
 *     <li><b>接口计时统计</b>（timing）：精确计算请求耗时，慢接口检测</li>
 *     <li><b>分布式追踪</b>（trace）：生成/传播 TraceId，构建 Span 树，MDC 集成</li>
 * </ul>
 * <p>
 * 四种运行模式：
 * <ol>
 *     <li>timing ON + trace ON：完整体验，Span 树 + 精确计时 + 慢接口检测</li>
 *     <li>timing ON + trace OFF：生成 requestId 写入 MDC，输出简洁计时摘要</li>
 *     <li>timing OFF + trace ON：创建 TraceContext + Span 树（Span 自带耗时）</li>
 *     <li>timing OFF + trace OFF：Filter 不注册（由 AutoConfiguration 控制）</li>
 * </ol>
 *
 * @author chen
 */
public class TraceFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(TraceFilter.class);

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_SPAN_ID = "X-Span-Id";
    public static final String HEADER_PARENT_SPAN_ID = "X-Parent-Span-Id";

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String MDC_REQUEST_ID = "requestId";

    private final LogTracingProperties properties;

    public TraceFilter(LogTracingProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean timingEnabled = false;
        boolean tracingEnabled = false;
        try {
            timingEnabled = properties.getTiming().isEnabled();
            tracingEnabled = properties.getTrace().isEnabled();
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] 读取配置异常，跳过追踪/计时", e);
        }

        // === 计时模块：记录起始时间 ===
        long startNanos = 0;
        if (timingEnabled) {
            startNanos = System.nanoTime();
        }

        // === 追踪模块：创建 TraceContext 和根 Span ===
        TraceContext context = null;
        SpanInfo rootSpan = null;
        String requestId = null;

        try {
            if (tracingEnabled) {
                // 从请求头提取或生成 TraceId
                String traceId = request.getHeader(HEADER_TRACE_ID);
                if (traceId == null || traceId.trim().isEmpty()) {
                    traceId = TraceIdGenerator.generateTraceId();
                }

                // 从请求头提取 ParentSpanId（跨服务场景）
                String parentSpanId = request.getHeader(HEADER_SPAN_ID);

                // 创建追踪上下文
                context = new TraceContext(traceId);
                TraceContext.setCurrent(context);

                // 写入 MDC
                MDC.put(MDC_TRACE_ID, traceId);

                // 创建根 Span
                String operationName = request.getMethod() + " " + request.getRequestURI();
                rootSpan = context.startSpan(operationName, SpanKind.SERVER);
                rootSpan.addTag("http.method", request.getMethod());
                rootSpan.addTag("http.url", request.getRequestURL().toString());
                if (parentSpanId != null && !parentSpanId.trim().isEmpty()) {
                    rootSpan.addTag("parent.span.id", parentSpanId);
                }

                MDC.put(MDC_SPAN_ID, rootSpan.getSpanId());

                // 响应头回写 TraceId
                response.setHeader(HEADER_TRACE_ID, traceId);
            } else if (timingEnabled) {
                // 仅计时模式：生成 requestId 写入 MDC，方便日志关联
                requestId = TraceIdGenerator.generateSpanId();
                MDC.put(MDC_REQUEST_ID, requestId);
            }
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] 追踪上下文初始化异常，跳过追踪", e);
        }

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            try {
                if (rootSpan != null) {
                    rootSpan.markError(e.getMessage());
                }
            } catch (Exception ex) {
                LOG.debug("[snow-logtracing] 标记异常 Span 失败", ex);
            }
            throw e;
        } finally {
            try {
                // === 追踪模块：结束根 Span ===
                if (tracingEnabled && context != null) {
                    context.finishSpan();
                    if (rootSpan != null) {
                        rootSpan.addTag("http.status", String.valueOf(response.getStatus()));
                    }
                }
            } catch (Exception e) {
                LOG.debug("[snow-logtracing] 结束 Span 异常", e);
            }

            // === 计算耗时 ===
            long durationMs = -1;
            if (timingEnabled) {
                durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            }

            // === 输出汇总日志 ===
            try {
                printSummary(request, response, context, rootSpan, requestId, durationMs, timingEnabled, tracingEnabled);
            } catch (Exception e) {
                LOG.debug("[snow-logtracing] 输出汇总日志异常", e);
            }

            // === 清理（必须执行） ===
            try {
                if (tracingEnabled) {
                    MDC.remove(MDC_TRACE_ID);
                    MDC.remove(MDC_SPAN_ID);
                    TraceContext.clear();
                }
                if (!tracingEnabled && timingEnabled) {
                    MDC.remove(MDC_REQUEST_ID);
                }
            } catch (Exception e) {
                LOG.debug("[snow-logtracing] 清理上下文异常", e);
            }
        }
    }

    // ========================== 日志输出 ==========================

    /**
     * 根据 timing/trace 开启状态输出不同格式的汇总日志
     */
    private void printSummary(HttpServletRequest request, HttpServletResponse response,
                              TraceContext context, SpanInfo rootSpan, String requestId,
                              long durationMs, boolean timingEnabled, boolean tracingEnabled) {
        if (tracingEnabled && context != null && rootSpan != null) {
            // 追踪模式（含或不含计时）
            printTraceSummary(context, rootSpan, durationMs, timingEnabled);
        } else if (timingEnabled) {
            // 仅计时模式
            String operationName = request.getMethod() + " " + request.getRequestURI();
            printTimingSummary(requestId, operationName, response.getStatus(), durationMs);
        }
    }

    /**
     * 追踪模式：输出完整的 Span 树汇总（含 traceId、Span 树、可选慢接口警告）
     */
    private void printTraceSummary(TraceContext context, SpanInfo rootSpan,
                                   long timingDurationMs, boolean timingEnabled) {
        if (!properties.getTrace().isSpanTreeLog()) {
            return;
        }

        // 耗时：优先使用 TimingFilter 的精确计时，否则使用根 Span 的耗时
        long durationMs = timingDurationMs >= 0 ? timingDurationMs : rootSpan.getDurationMs();

        int status = 200;
        try {
            status = Integer.parseInt(rootSpan.getTags().getOrDefault("http.status", "200"));
        } catch (NumberFormatException ignored) {
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(BORDER).append("\n");

        // traceId
        appendField(sb, "traceId", "  : ", CYAN, context.getTraceId());
        appendField(sb, "请求   ", "  : ", WHITE_BOLD, rootSpan.getOperationName());
        appendStatusLine(sb, status);
        appendDurationLine(sb, "总耗时", durationMs);
        appendSlowWarning(sb, durationMs, timingEnabled);
        sb.append("\n");

        // Span 调用链
        List<SpanInfo> children = rootSpan.getChildren();
        if (!children.isEmpty()) {
            sb.append(GRAY).append("  ─────────────────────────────────────────────────────────────────").append(RESET).append("\n");
            buildSpanTree(sb, rootSpan, "  ", true);
        }

        // 输出响应体（如果有）
        String responseBody = context.getResponseBody();
        if (responseBody != null && !responseBody.isEmpty()) {
            sb.append(GRAY).append("  ─────────────────────────────────────────────────────────────────").append(RESET).append("\n");
            sb.append(CYAN).append("  返回结果 : ").append(RESET).append(responseBody).append("\n");
        }

        sb.append(BORDER);
        LOG.info(sb.toString());
    }

    /**
     * 仅计时模式：输出简洁的计时摘要（requestId、请求、状态、耗时）
     */
    private void printTimingSummary(String requestId, String operationName, int status, long durationMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(BORDER).append("\n");

        appendField(sb, "requestId", ": ", CYAN, requestId);
        appendField(sb, "请求   ", "  : ", WHITE_BOLD, operationName);
        appendStatusLine(sb, status);
        appendDurationLine(sb, "耗时  ", durationMs);
        appendSlowWarning(sb, durationMs, true);
        sb.append("\n");

        sb.append(BORDER);
        LOG.info(sb.toString());
    }

    // ========================== 日志构建辅助方法 ==========================

    /** 追加一行标签字段：标签名 + 着色值 */
    private static void appendField(StringBuilder sb, String label, String separator, String color, String value) {
        sb.append(GRAY).append("  ").append(label).append(separator).append(RESET)
                .append(color).append(value).append(RESET).append("\n");
    }

    /** 追加状态码行，按状态码范围着色 */
    private static void appendStatusLine(StringBuilder sb, int status) {
        String statusColor = status < 400 ? GREEN : (status < 500 ? YELLOW : RED);
        sb.append(GRAY).append("  ").append("状态码   : ").append(RESET)
                .append(statusColor).append(status).append(RESET).append("\n");
    }

    /** 追加耗时行，按耗时区间着色 */
    private static void appendDurationLine(StringBuilder sb, String label, long durationMs) {
        String timeColor = durationMs < 200 ? GREEN : (durationMs < 1000 ? YELLOW : RED);
        sb.append(GRAY).append("  ").append(label).append("   : ").append(RESET)
                .append(timeColor).append(durationMs).append("ms").append(RESET);
    }

    /** 追加慢接口警告（如果超过阈值） */
    private void appendSlowWarning(StringBuilder sb, long durationMs, boolean check) {
        if (!check) {
            return;
        }
        long slowThreshold = properties.getTiming().getSlowThreshold();
        if (slowThreshold > 0 && durationMs > slowThreshold) {
            sb.append(" ").append(RED).append("⚠ 慢接口（阈值 ").append(slowThreshold).append("ms）").append(RESET);
        }
    }

    /**
     * 递归构建 Span 树状结构
     */
    private void buildSpanTree(StringBuilder sb, SpanInfo span, String prefix, boolean isRoot) {
        if (!isRoot) {
            long ms = span.getDurationMs();
            String timeColor = ms < 100 ? GREEN : (ms < 500 ? YELLOW : RED);
            String kindTag;
            switch (span.getKind()) {
                case SERVER:
                    kindTag = CYAN + "SERVER" + RESET;
                    break;
                case CLIENT:
                    kindTag = YELLOW + "CLIENT" + RESET;
                    break;
                case INTERNAL:
                    kindTag = GREEN + "INTERNAL" + RESET;
                    break;
                default:
                    kindTag = span.getKind().toString();
            }

            sb.append(GRAY).append(prefix).append(RESET);
            sb.append(kindTag);
            sb.append(GRAY).append(" ▸ ").append(RESET);
            sb.append(span.getOperationName());
            sb.append(GRAY).append(" ─ ").append(RESET);
            sb.append(timeColor).append(ms).append("ms").append(RESET);
            if (span.isError()) {
                sb.append(" ").append(RED).append("✗ ").append(span.getErrorMessage()).append(RESET);
            }
            sb.append("\n");
        }

        List<SpanInfo> children = span.getChildren();
        for (int i = 0; i < children.size(); i++) {
            SpanInfo child = children.get(i);
            boolean isLast = (i == children.size() - 1);
            String connector = isLast ? "└─ " : "├─ ";
            String childPrefix;
            if (isRoot) {
                childPrefix = connector;
            } else {
                childPrefix = prefix.replace("├─ ", "│  ").replace("└─ ", "   ") + connector;
            }
            buildSpanTree(sb, child, childPrefix, false);
        }
    }
}
