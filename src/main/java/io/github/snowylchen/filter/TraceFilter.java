package io.github.snowylchen.filter;

import io.github.snowylchen.config.LogTracingProperties;
import io.github.snowylchen.trace.SpanInfo;
import io.github.snowylchen.trace.SpanKind;
import io.github.snowylchen.trace.TraceContext;
import io.github.snowylchen.trace.TraceIdGenerator;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 追踪过滤器，作为请求入口负责：
 * 1. 生成/提取 TraceId 和 SpanId
 * 2. 写入 MDC 使日志自动携带追踪信息
 * 3. 使用 System.nanoTime() 精确计时
 * 4. 响应头回写 X-Trace-Id
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

    private final LogTracingProperties properties;

    public TraceFilter(LogTracingProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        // 确保在其他 Filter 之前执行
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 从请求头提取或生成 TraceId
        String traceId = request.getHeader(HEADER_TRACE_ID);
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = TraceIdGenerator.generateTraceId();
        }

        // 从请求头提取 ParentSpanId（跨服务场景）
        String parentSpanId = request.getHeader(HEADER_SPAN_ID);

        // 创建追踪上下文
        TraceContext context = new TraceContext(traceId);
        TraceContext.setCurrent(context);

        // 写入 MDC
        MDC.put(MDC_TRACE_ID, traceId);

        // 创建根 Span
        String operationName = request.getMethod() + " " + request.getRequestURI();
        SpanInfo rootSpan = context.startSpan(operationName, SpanKind.SERVER);
        rootSpan.addTag("http.method", request.getMethod());
        rootSpan.addTag("http.url", request.getRequestURL().toString());
        if (parentSpanId != null && !parentSpanId.trim().isEmpty()) {
            rootSpan.addTag("parent.span.id", parentSpanId);
        }

        MDC.put(MDC_SPAN_ID, rootSpan.getSpanId());

        // 响应头回写 TraceId，方便前端/调用方关联
        response.setHeader(HEADER_TRACE_ID, traceId);

        try {
            // 精确计时：只包裹 filterChain.doFilter
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            rootSpan.markError(e.getMessage());
            throw e;
        } finally {
            // 结束根 Span
            context.finishSpan();
            rootSpan.addTag("http.status", String.valueOf(response.getStatus()));

            // 输出追踪汇总日志
            printTraceSummary(context, rootSpan);

            // 清理，防止 ThreadLocal 泄漏
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
            TraceContext.clear();
        }
    }

    // ANSI 颜色常量
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String GRAY = "\u001B[90m";
    private static final String WHITE_BOLD = "\u001B[1;37m";

    private static final String BORDER = GRAY + "═══════════════════════════════════════════════════════════════════════" + RESET;

    /**
     * 输出追踪调用链汇总日志
     */
    private void printTraceSummary(TraceContext context, SpanInfo rootSpan) {
        if (properties != null && Boolean.FALSE.equals(properties.getEnable())) {
            return;
        }

        long durationMs = rootSpan.getDurationMs();
        int status = 200;
        try {
            status = Integer.parseInt(rootSpan.getTags().getOrDefault("http.status", "200"));
        } catch (NumberFormatException ignored) {
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(BORDER).append("\n");

        // traceId
        sb.append(GRAY).append("  ").append("traceId").append("  : ").append(RESET)
                .append(CYAN).append(context.getTraceId()).append(RESET).append("\n");

        // 请求
        sb.append(GRAY).append("  ").append("请求     : ").append(RESET)
                .append(WHITE_BOLD).append(rootSpan.getOperationName()).append(RESET).append("\n");

        // 状态码
        String statusColor = status < 400 ? GREEN : (status < 500 ? YELLOW : RED);
        sb.append(GRAY).append("  ").append("状态码   : ").append(RESET)
                .append(statusColor).append(status).append(RESET).append("\n");

        // 总耗时
        String timeColor = durationMs < 200 ? GREEN : (durationMs < 1000 ? YELLOW : RED);
        sb.append(GRAY).append("  ").append("总耗时   : ").append(RESET)
                .append(timeColor).append(durationMs).append("ms").append(RESET).append("\n");

        // Span 调用链
        List<SpanInfo> children = rootSpan.getChildren();
        if (!children.isEmpty()) {
            sb.append(GRAY).append("  ─────────────────────────────────────────────────────────────────").append(RESET).append("\n");
            buildSpanTree(sb, rootSpan, "  ", true);
        }

        sb.append(BORDER);
        LOG.info(sb.toString());
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
                String continuation = isLast ? "   " : "│  ";
                childPrefix = prefix.replace("├─ ", "│  ").replace("└─ ", "   ") + connector;
            }
            buildSpanTree(sb, child, childPrefix, false);
        }
    }
}
