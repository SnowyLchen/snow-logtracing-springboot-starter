package io.github.snowylchen.aspect;

import io.github.snowylchen.annotation.Trace;
import io.github.snowylchen.trace.SpanInfo;
import io.github.snowylchen.trace.SpanKind;
import io.github.snowylchen.trace.TraceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

/**
 * @Trace 注解切面，用于 Service/DAO 层方法级追踪
 * 创建 INTERNAL 类型的子 Span，精确记录方法耗时
 *
 * @author chen
 */
@Aspect
@Order(1)
public class TraceAnnotationAspect {

    private static final Logger LOG = LoggerFactory.getLogger(TraceAnnotationAspect.class);

    @Around("@annotation(trace)")
    public Object traceMethod(ProceedingJoinPoint joinPoint, Trace trace) throws Throwable {
        TraceContext context = TraceContext.getCurrent();
        if (context == null) {
            // 没有追踪上下文，直接执行
            return joinPoint.proceed();
        }

        SpanInfo span = null;
        try {
            // 确定操作名
            String operationName = trace.value();
            if (operationName == null || operationName.trim().isEmpty()) {
                Signature signature = joinPoint.getSignature();
                operationName = signature.getDeclaringType().getSimpleName() + "#" + signature.getName();
            }

            // 创建子 Span
            span = context.startSpan(operationName, SpanKind.INTERNAL);

            // 添加方法信息标签
            Signature signature = joinPoint.getSignature();
            if (signature instanceof MethodSignature) {
                Method method = ((MethodSignature) signature).getMethod();
                span.addTag("method.class", method.getDeclaringClass().getName());
                span.addTag("method.name", method.getName());
            }
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] @Trace Span 创建异常", e);
        }

        try {
            // 精确计时：只包裹 joinPoint.proceed()
            Object result = joinPoint.proceed();
            try {
                if (span != null) {
                    context.finishSpan();
                }
            } catch (Exception e) {
                LOG.debug("[snow-logtracing] @Trace Span 结束异常", e);
            }
            return result;
        } catch (Throwable e) {
            try {
                if (span != null) {
                    span.markError(e.getMessage());
                    context.finishSpan();
                }
            } catch (Exception ex) {
                LOG.debug("[snow-logtracing] @Trace Span 异常处理失败", ex);
            }
            throw e;
        }
    }
}
