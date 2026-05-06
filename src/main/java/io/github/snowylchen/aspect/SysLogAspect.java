package io.github.snowylchen.aspect;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.filter.SimplePropertyPreFilter;
import io.github.snowylchen.annotation.SysLog;
import io.github.snowylchen.config.LogTracingProperties;
import io.github.snowylchen.syslog.OperatorProvider;
import io.github.snowylchen.syslog.SysLogEvent;
import io.github.snowylchen.syslog.SysLogExpressionEvaluator;
import io.github.snowylchen.syslog.SysLogInfo;
import io.github.snowylchen.trace.TraceContext;
import io.github.snowylchen.util.LogServletUtils;
import io.github.snowylchen.util.WebUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统操作日志切面
 * <p>
 * 异常安全：所有插件逻辑均被 try-catch 包裹，绝不影响业务方法执行。
 * 性能优化：注解解析结果缓存 + 脱敏过滤器缓存，避免每次请求重复反射/创建对象。
 *
 * @author chen
 */
@Aspect
@Order(0)
public class SysLogAspect {

    private static final Logger LOG = LoggerFactory.getLogger(SysLogAspect.class);

    private final ApplicationEventPublisher publisher;
    private final LogTracingProperties properties;
    private final OperatorProvider operatorProvider;
    private final SysLogExpressionEvaluator evaluator = new SysLogExpressionEvaluator();

    /** 注解解析缓存：Method → Optional<SysLog>，避免每次请求反射查找 */
    private final Map<Method, Optional<SysLog>> annotationCache = new ConcurrentHashMap<>();

    /** 脱敏过滤器缓存，配置运行时不变，无需反复创建 */
    private volatile SimplePropertyPreFilter sensitiveFilterCache;

    public SysLogAspect(ApplicationEventPublisher publisher,
                        LogTracingProperties properties,
                        OperatorProvider operatorProvider) {
        this.publisher = publisher;
        this.properties = properties;
        this.operatorProvider = operatorProvider;
    }

    // ========================== 切面入口 ==========================

    @Around("@within(io.github.snowylchen.annotation.SysLog) || @annotation(io.github.snowylchen.annotation.SysLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 解析注解（带缓存）
        SysLog sysLog;
        try {
            sysLog = resolveAnnotation(joinPoint);
            if (sysLog == null) {
                return joinPoint.proceed();
            }
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] @SysLog 注解解析异常", e);
            return joinPoint.proceed();
        }

        // 2. 构建日志信息（失败不影响业务）
        SysLogInfo logInfo = null;
        long startNanos = System.nanoTime();
        try {
            logInfo = buildLogInfo(joinPoint, sysLog);
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] SysLogInfo 构建异常", e);
        }

        // 3. 执行业务方法
        try {
            Object result = joinPoint.proceed();
            recordSuccess(logInfo, result, startNanos, sysLog);
            return result;
        } catch (Throwable e) {
            recordError(logInfo, e, startNanos);
            throw e; // 业务异常必须原样抛出
        }
    }

    // ========================== 注解解析（带缓存） ==========================

    private SysLog resolveAnnotation(ProceedingJoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        if (!(signature instanceof MethodSignature)) {
            return null;
        }
        Method method = ((MethodSignature) signature).getMethod();

        return annotationCache
                .computeIfAbsent(method, this::findSysLogAnnotation)
                .orElse(null);
    }

    /**
     * 查找 @SysLog 注解：方法级优先于类级
     */
    private Optional<SysLog> findSysLogAnnotation(Method method) {
        SysLog methodSysLog = AnnotatedElementUtils.findMergedAnnotation(method, SysLog.class);
        if (methodSysLog != null) {
            return Optional.of(methodSysLog);
        }
        SysLog classSysLog = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), SysLog.class);
        return Optional.ofNullable(classSysLog);
    }

    // ========================== 日志信息构建 ==========================

    private SysLogInfo buildLogInfo(ProceedingJoinPoint joinPoint, SysLog sysLog) {
        SysLogInfo logInfo = new SysLogInfo();
        logInfo.setLogId(IdUtil.fastSimpleUUID());
        logInfo.setTraceId(TraceContext.currentTraceId());
        logInfo.setOperateTime(LocalDateTime.now());

        Signature signature = joinPoint.getSignature();
        Method method = ((MethodSignature) signature).getMethod();

        logInfo.setClassName(signature.getDeclaringTypeName());
        logInfo.setMethodName(signature.getName());
        logInfo.setOperationType(sysLog.type());
        logInfo.setModule(sysLog.module());

        logInfo.setDescription(resolveDescription(sysLog, signature, method, joinPoint.getArgs()));
        fillRequestInfo(logInfo);
        fillOperatorInfo(logInfo);
        fillParams(logInfo, joinPoint, sysLog);

        return logInfo;
    }

    /**
     * 解析操作描述：为空时使用 "类名#方法名"，否则走 SpEL 解析
     */
    private String resolveDescription(SysLog sysLog, Signature signature, Method method, Object[] args) {
        String desc = sysLog.value();
        if (desc == null || desc.trim().isEmpty()) {
            return signature.getDeclaringType().getSimpleName() + "#" + signature.getName();
        }
        return evaluator.evaluate(desc, method, args);
    }

    /**
     * 填充 HTTP 请求信息
     */
    private void fillRequestInfo(SysLogInfo logInfo) {
        try {
            HttpServletRequest request = LogServletUtils.getHttpServletRequest();
            if (request == null) {
                return;
            }
            logInfo.setRequestUrl(request.getRequestURI());
            logInfo.setRequestMethod(request.getMethod());
            logInfo.setClientIp(WebUtil.getIP(request));
            logInfo.setUserAgent(request.getHeader(WebUtil.USER_AGENT_HEADER));
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] 获取请求信息失败", e);
        }
    }

    /**
     * 填充操作人信息
     */
    private void fillOperatorInfo(SysLogInfo logInfo) {
        try {
            if (operatorProvider == null) {
                return;
            }
            logInfo.setOperatorId(operatorProvider.getOperatorId());
            logInfo.setOperatorName(operatorProvider.getOperatorName());
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] 获取操作人信息失败", e);
        }
    }

    /**
     * 填充请求参数
     */
    private void fillParams(SysLogInfo logInfo, ProceedingJoinPoint joinPoint, SysLog sysLog) {
        if (!sysLog.saveParams()) {
            return;
        }
        try {
            logInfo.setParams(serializeParams(joinPoint));
        } catch (Exception e) {
            logInfo.setParams("[serialization error]");
            LOG.debug("[snow-logtracing] 参数序列化异常", e);
        }
    }

    // ========================== 结果记录 ==========================

    private void recordSuccess(SysLogInfo logInfo, Object result, long startNanos, SysLog sysLog) {
        try {
            if (logInfo == null) return;
            logInfo.setSuccess(true);
            logInfo.setCostTime((System.nanoTime() - startNanos) / 1_000_000);

            if (sysLog.saveResult() && result != null) {
                try {
                    logInfo.setResult(JSON.toJSONString(result, getSensitiveFilter()));
                } catch (Exception e) {
                    logInfo.setResult("[serialization error]");
                    LOG.debug("[snow-logtracing] 结果序列化异常", e);
                }
            }

            publisher.publishEvent(new SysLogEvent(logInfo));
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] 日志记录（成功分支）异常", e);
        }
    }

    private void recordError(SysLogInfo logInfo, Throwable e, long startNanos) {
        try {
            if (logInfo == null) return;
            logInfo.setSuccess(false);
            logInfo.setCostTime((System.nanoTime() - startNanos) / 1_000_000);
            logInfo.setErrorMessage(e.getMessage());

            publisher.publishEvent(new SysLogEvent(logInfo));
        } catch (Exception ex) {
            LOG.debug("[snow-logtracing] 日志记录（异常分支）异常", ex);
        }
    }

    // ========================== 工具方法 ==========================

    /**
     * 序列化方法参数为 JSON（过滤不可序列化类型 + 脱敏）
     */
    private String serializeParams(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = ((CodeSignature) joinPoint.getSignature()).getParameterNames();
        Map<String, Object> params = new LinkedHashMap<>();

        for (int i = 0; i < args.length; i++) {
            if (isNonSerializableType(args[i])) {
                continue;
            }
            if (parameterNames[i] != null && args[i] != null) {
                params.put(parameterNames[i], args[i]);
            }
        }
        return JSON.toJSONString(params, getSensitiveFilter());
    }

    /**
     * 判断参数是否为不可序列化的 Servlet/文件类型
     */
    private boolean isNonSerializableType(Object arg) {
        return arg instanceof ServletRequest
                || arg instanceof ServletResponse
                || arg instanceof MultipartFile;
    }

    /**
     * 获取脱敏过滤器（懒加载 + 缓存，配置运行时不变无需重复创建）
     */
    private SimplePropertyPreFilter getSensitiveFilter() {
        if (sensitiveFilterCache != null) {
            return sensitiveFilterCache;
        }

        SimplePropertyPreFilter filter = new SimplePropertyPreFilter();
        if (properties != null) {
            filter.getExcludes().addAll(properties.getAllSensitiveFields());
        } else {
            filter.getExcludes().add("password");
            filter.getExcludes().add("token");
            filter.getExcludes().add("secret");
        }
        sensitiveFilterCache = filter;
        return filter;
    }
}
