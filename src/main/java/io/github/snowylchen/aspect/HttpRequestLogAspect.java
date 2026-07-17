package io.github.snowylchen.aspect;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.filter.SimplePropertyPreFilter;
import io.github.snowylchen.config.LogTracingProperties;
import io.github.snowylchen.trace.SpanInfo;
import io.github.snowylchen.trace.SpanKind;
import io.github.snowylchen.trace.TraceContext;
import io.github.snowylchen.util.LogServletUtils;
import io.github.snowylchen.util.WebUtil;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.snowylchen.util.LogUtil.*;

@Aspect
@Order()
public class HttpRequestLogAspect {
    private final static Logger LOG = LoggerFactory.getLogger(HttpRequestLogAspect.class);
    private static final ThreadLocal<StringBuilder> LOG_BUFFER = ThreadLocal.withInitial(StringBuilder::new);
    /**
     * 方法行号缓存，避免重复解析字节码
     */
    private static final ConcurrentHashMap<String, Integer> METHOD_LINE_CACHE = new ConcurrentHashMap<>();

    /**
     * 日志配置属性
     */
    private static LogTracingProperties logTracingProperties;

    @Autowired(required = false)
    public void setLogTracingProperties(LogTracingProperties logTracingProperties) {
        HttpRequestLogAspect.logTracingProperties = logTracingProperties;
    }

    /**
     * 定义一个切点
     */
    @Pointcut("execution(public * *..controller..*Controller.*(..))")
    public void controllerPointcut() {
    }

    public static StringBuilder buildRequestLog(JoinPoint joinPoint, HttpServletRequest request, Signature signature, String name) {
        StringBuilder logBuffer = LOG_BUFFER.get();
        logBuffer.setLength(0);

        try {
            // 检查是否启用 HTTP 请求日志输出
            if (logTracingProperties != null && !logTracingProperties.getRequestLog().isEnabled()) {
                return logBuffer;
            }

            if (request == null) {
                return logBuffer;
            }

            // 构建请求开始信息（使用边框样式）
            logBuffer.append("\n").append(BORDER).append("\n");
            logBuffer.append(GRAY).append("  ").append(WHITE_BOLD).append("请求开始").append(RESET).append("\n");
            logBuffer.append(GRAY).append("  ─────────────────────────────────────────────────────────────────").append(RESET).append("\n");

            // 添加 traceId 信息
            String traceId = TraceContext.currentTraceId();
            if (traceId != null) {
                logBuffer.append(CYAN).append("  traceId   : ").append(RESET).append(traceId).append("\n");
            }

            // 根据配置动态添加字段
            if (shouldOutput("requestUrl")) {
                logBuffer.append(GRAY).append("  请求地址   : ").append(RESET)
                        .append(WHITE_BOLD).append(request.getMethod()).append(" ").append(request.getRequestURL().toString()).append(RESET).append("\n");
            }

            if (shouldOutput("methodInfo")) {
                logBuffer.append(GRAY).append("  类名方法   : ").append(RESET)
                        .append(signature.getDeclaringTypeName()).append("#").append(name).append("\n");
            }

            // 获取方法的真实行号
            int lineNumber = getMethodLineNumber(signature);
            if (shouldOutput("lineInfo")) {
                logBuffer.append(GRAY).append("  代码位置   : ").append(RESET)
                        .append(signature.getDeclaringType().getSimpleName()).append(".java:").append(lineNumber).append("\n");
            }

            if (shouldOutput("remoteIp")) {
                logBuffer.append(GRAY).append("  远程地址   : ").append(RESET).append(WebUtil.getIP(request)).append("\n");
            }

            if (shouldOutput("headers")) {
                logBuffer.append(GRAY).append("  请求头信息 : ").append(RESET).append(extractHeadersInfo(request)).append("\n");
            }

            if (shouldOutput("params")) {
                logBuffer.append(GRAY).append("  请求参数   : ").append(RESET).append(buildRequestParam(joinPoint)).append("\n");
            }

            logBuffer.append(BORDER);
            LOG.info(logBuffer.toString());
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] 构建请求日志异常", e);
        }
        return logBuffer;
    }

    /**
     * 获取方法的真实行号（带缓存）
     */
    private static int getMethodLineNumber(Signature signature) {
        try {
            if (signature instanceof MethodSignature) {
                MethodSignature methodSignature = (MethodSignature) signature;
                Method method = methodSignature.getMethod();
                // 构建缓存key: 类名#方法名#方法描述符
                String cacheKey = method.getDeclaringClass().getName() + "#" + method.getName()
                        + "#" + org.springframework.asm.Type.getMethodDescriptor(method);

                // 从缓存获取
                return METHOD_LINE_CACHE.computeIfAbsent(cacheKey, key -> parseMethodLineNumber(method));
            }
        } catch (Exception e) {
            LOG.debug("获取方法行号失败", e);
        }
        return 1;
    }

    /**
     * 解析方法行号（仅首次调用时执行）
     */
    private static int parseMethodLineNumber(Method method) {
        try {
            Class<?> declaringClass = method.getDeclaringClass();
            String methodName = method.getName();
            String methodDesc = org.springframework.asm.Type.getMethodDescriptor(method);

            String classFile = declaringClass.getName().replace('.', '/') + ".class";
            try (InputStream is = declaringClass.getClassLoader().getResourceAsStream(classFile)) {
                if (is != null) {
                    ClassReader classReader = new ClassReader(is);
                    final int[] lineNumber = {1};
                    classReader.accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String sig, String[] exceptions) {
                            if (name.equals(methodName) && descriptor.equals(methodDesc)) {
                                return new MethodVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visitLineNumber(int line, org.springframework.asm.Label start) {
                                        if (lineNumber[0] == 1) {
                                            lineNumber[0] = line;
                                        }
                                    }
                                };
                            }
                            return null;
                        }
                    }, ClassReader.SKIP_FRAMES);
                    return lineNumber[0];
                }
            }
        } catch (Exception e) {
            LOG.debug("解析方法行号失败", e);
        }
        return 1;
    }


    @Around("controllerPointcut()")
    public Object doAround(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        try {
            // 检查是否启用 HTTP 请求日志输出
            if (logTracingProperties != null && !logTracingProperties.getRequestLog().isEnabled()) {
                return proceedingJoinPoint.proceed();
            }
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] 读取日志配置异常，直接执行业务方法", e);
            return proceedingJoinPoint.proceed();
        }
        return printHttpRequestLogFormat(proceedingJoinPoint);
    }

    /**
     * 判断是否需要输出指定字段（配置为空时默认输出所有字段）
     */
    private static boolean shouldOutput(String fieldName) {
        return logTracingProperties == null || logTracingProperties.needOutput(fieldName);
    }


    /**
     * 通过切面获取请求参数
     */
    public static String buildRequestParam(JoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            // 获取请求参数key
            String[] parameterNames = ((CodeSignature) joinPoint.getSignature()).getParameterNames();
            // 组装请求参数
            JSONObject params = new JSONObject();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof ServletRequest
                        || args[i] instanceof ServletResponse
                        || args[i] instanceof MultipartFile) {
                    continue;
                }
                if (parameterNames[i] != null && args[i] != null) {
                    params.put(parameterNames[i], args[i]);
                }
            }
            return JSON.toJSONString(params, buildSensitiveInfoFilter());
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] 构建请求参数日志异常", e);
            return "[error: " + e.getMessage() + "]";
        }
    }

    /**
     * 构建 headers 信息
     */
    private static String extractHeadersInfo(HttpServletRequest request) {
        // 逐行打印请求头信息
        Enumeration<String> headerNames = request.getHeaderNames();

        // 获取需要排除的 Header 列表（默认 + 自定义）
        List<String> excludeHeaderList = new ArrayList<>();
        if (logTracingProperties != null) {
            excludeHeaderList.addAll(logTracingProperties.getAllExcludeHeaders());
        } else {
            // 默认排除的 header
            excludeHeaderList.add("content-length");
            excludeHeaderList.add("connection");
            excludeHeaderList.add("accept");
            excludeHeaderList.add("cache-control");
            excludeHeaderList.add("accept-encoding");
            excludeHeaderList.add("accept-language");
            excludeHeaderList.add("upgrade-insecure-requests");
        }

        JSONObject headers = new JSONObject();
        while (headerNames.hasMoreElements()) {
            String key = headerNames.nextElement();
            String value = request.getHeader(key);
            if (excludeHeaderList.contains(key.toLowerCase())) {
                continue;
            }
            headers.put(key, value);
        }
        return JSON.toJSONString(headers, buildSensitiveInfoFilter());
    }

    /**
     * 构建敏感信息过滤
     */
    private static SimplePropertyPreFilter buildSensitiveInfoFilter() {
        // 当某些字段太敏感，或者是太长时，就不显示
        List<String> excludeProperties = new ArrayList<>();
        if (logTracingProperties != null) {
            // 使用配置中的敏感字段列表（默认 + 自定义）
            excludeProperties.addAll(logTracingProperties.getAllSensitiveFields());
        } else {
            // 默认值
            excludeProperties.add("password");
            excludeProperties.add("token");
            excludeProperties.add("secret");
        }

        SimplePropertyPreFilter filters = new SimplePropertyPreFilter();
        for (String str : excludeProperties) {
            filters.getExcludes().add(str);
        }
        return filters;
    }


    /**
     * 打印请求日志（计时由 TraceFilter 负责，这里只负责请求参数和响应体的日志输出）
     * 同时自动为 Controller 方法创建子 Span，使 Span 树至少有一层
     */
    private static <T> T printHttpRequestLogFormat(ProceedingJoinPoint joinPoint) throws Throwable {
        // 前置日志输出（失败不影响业务）
        TraceContext context = null;
        SpanInfo controllerSpan = null;
        try {
            HttpServletRequest request = LogServletUtils.getHttpServletRequest();
            Signature signature = joinPoint.getSignature();
            String name = signature.getName();

            // 输出请求开始日志（参数、Header 等）
            buildRequestLog(joinPoint, request, signature, name);

            // 自动创建 Controller 层子 Span
            context = TraceContext.getCurrent();
            if (context != null) {
                String operationName = signature.getDeclaringType().getSimpleName() + "#" + name;
                controllerSpan = context.startSpan(operationName, SpanKind.INTERNAL);
                controllerSpan.addTag("layer", "controller");
            }
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] 前置日志/Span 创建异常", e);
        }

        try {
            // 执行目标方法
            @SuppressWarnings("unchecked")
            T result = (T) joinPoint.proceed();

            // 结束 Controller Span
            try {
                if (context != null) {
                    context.finishSpan();
                }
            } catch (Exception e) {
                LOG.debug("[snow-logtracing] 结束 Controller Span 异常", e);
            }

            // 存储响应体到 TraceContext，供 TraceFilter 汇总输出（不再单独输出日志）
            try {
                boolean needResponse = shouldOutput("response") && (result != null);
                if (needResponse && context != null) {
                    String responseJson = JSON.toJSONString(result, buildSensitiveInfoFilter());
                    context.setResponseBody(responseJson);
                }
            } catch (Exception e) {
                LOG.debug("[snow-logtracing] 存储响应体异常", e);
            }

            return result;
        } catch (Throwable e) {
            try {
                if (controllerSpan != null) {
                    controllerSpan.markError(e.getMessage());
                }
                if (context != null) {
                    context.finishSpan();
                }
            } catch (Exception ex) {
                LOG.debug("[snow-logtracing] 异常处理中结束 Span 失败", ex);
            }
            throw e;
        }
    }
}
