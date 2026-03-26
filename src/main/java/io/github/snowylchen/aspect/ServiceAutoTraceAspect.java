package io.github.snowylchen.aspect;

import io.github.snowylchen.trace.SpanInfo;
import io.github.snowylchen.trace.SpanKind;
import io.github.snowylchen.trace.TraceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;

/**
 * Service 层自动追踪切面
 * 通过配置 snow.logtracing.trace.auto-trace-service=true 开启，
 * 自动拦截 *..service..*Service 的 public 方法，创建 INTERNAL 子 Span，无需手动添加 @Trace 注解
 *
 * @author chen
 */
@Aspect
@Order(2)
public class ServiceAutoTraceAspect {

    @Pointcut("execution(public * *..service..*Service.*(..))")
    public void servicePointcut() {
    }

    @Around("servicePointcut()")
    public Object traceServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        TraceContext context = TraceContext.getCurrent();
        if (context == null) {
            return joinPoint.proceed();
        }

        Signature signature = joinPoint.getSignature();
        String operationName = signature.getDeclaringType().getSimpleName() + "#" + signature.getName();

        SpanInfo span = context.startSpan(operationName, SpanKind.INTERNAL);
        span.addTag("layer", "service");

        try {
            Object result = joinPoint.proceed();
            context.finishSpan();
            return result;
        } catch (Throwable e) {
            span.markError(e.getMessage());
            context.finishSpan();
            throw e;
        }
    }
}
