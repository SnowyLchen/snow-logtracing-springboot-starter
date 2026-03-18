package io.github.snowylchen.aspect;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.filter.SimplePropertyPreFilter;
import io.github.snowylchen.config.LogTracingProperties;
import io.github.snowylchen.util.FunExecuteTimeUtil;
import io.github.snowylchen.util.LogServletUtils;
import io.github.snowylchen.util.WebUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.snowylchen.util.LogUtil.*;

@Aspect
@Component
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

        // 检查是否启用日志追踪
        if (logTracingProperties != null && Boolean.FALSE.equals(logTracingProperties.getEnable())) {
            return logBuffer;
        }

        // 构建请求开始信息
        logBuffer.append(requestLog(REQUEST_START)).append("\n");
        ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

        // 根据配置动态添加字段
        if (logTracingProperties == null || logTracingProperties.needOutput("requestUrl")) {
            map.put("请求地址", request.getMethod() + " " + request.getRequestURL().toString());
        }

        if (logTracingProperties == null || logTracingProperties.needOutput("methodInfo")) {
            map.put("类名方法", "(" + signature.getDeclaringTypeName() + "#" + name + ")");
        }

        // 获取方法的真实行号
        int lineNumber = getMethodLineNumber(signature);
        if (logTracingProperties == null || logTracingProperties.needOutput("lineInfo")) {
            map.put("类名快捷跳转", "(" + signature.getDeclaringType().getSimpleName() + ".java:" + lineNumber + ")");
        }

        if (logTracingProperties == null || logTracingProperties.needOutput("remoteIp")) {
            map.put("远程地址", getRemoteIp(request));
        }

        if (logTracingProperties == null || logTracingProperties.needOutput("headers")) {
            map.put("请求头信息", extractHeadersInfo(request));
        }

        if (logTracingProperties == null || logTracingProperties.needOutput("params")) {
            map.put("请求的参数", buildRequestParam(joinPoint));
        }

        // 构建彩色的时间戳和线程信息
        for (Map.Entry<String, String> mp : map.entrySet()) {
            logBuffer.append(buildThreadLog())
                    .append(mp.getKey()).append(": ").append(mp.getValue()).append("\n");
        }
        LOG.info(logBuffer.toString());
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

    public static StringBuilder buildThreadLog() {
        StringBuilder logBuffer = new StringBuilder();
        // 构建彩色的时间戳和线程信息
        String colorfulTimestamp = DateUtil.now();
        String threadInfo = Thread.currentThread().getName();
        String colorfulThreadInfo = String.format(BLUE_LOG, threadInfo);
        logBuffer.append(colorfulTimestamp).append("\t")
                .append("[")
                .append(colorfulThreadInfo).append("]\t")
                .append(String.format(GREEN_LOG, HttpRequestLogAspect.class.getName())).append("\t")
                .append("\t:");
        return logBuffer;
    }

    @Around("controllerPointcut()")
    public Object doAround(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        // 检查是否启用日志追踪
        if (logTracingProperties != null && Boolean.FALSE.equals(logTracingProperties.getEnable())) {
            return proceedingJoinPoint.proceed();
        }
        return printHttpRequestLogFormat(proceedingJoinPoint, proceedingJoinPoint::proceed);
    }

    /**
     * 使用 Nginx 进行反向代理，这个方法主要是用来获取远程 IP
     *
     * @param request
     * @return
     */
    private static String getRemoteIp(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = WebUtil.getIP(request);
        }
        return ip;
    }


    /**
     * 通过切面获取请求参数
     */
    public static String buildRequestParam(JoinPoint joinPoint) {
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
     * 打印请求日志
     */
    private static <T> T printHttpRequestLogFormat(JoinPoint joinPoint, FunExecuteTimeUtil.CalculateTimeInterFace<T> calculateTimeInterFace) throws Throwable {
        HttpServletRequest request = LogServletUtils.getHttpServletRequest();
        long startTime = System.currentTimeMillis();
        Signature signature = joinPoint.getSignature();
        String name = signature.getName();
        StringBuilder requestThreadLog = buildRequestLog(joinPoint, request, signature, name);
        T result = calculateTimeInterFace.execute();
        requestThreadLog.setLength(0);

        // 检查是否需要输出响应结果
        boolean needResponse = (logTracingProperties == null ||
                logTracingProperties.needOutput("response")) && (result != null);

        if (needResponse) {
            requestThreadLog
                    .append("返回的结果:")
                    .append(JSON.toJSONString(result, buildSensitiveInfoFilter()))
                    .append("\n")
                    .append(buildThreadLog())
            ;
        }

        // 检查是否需要输出耗时信息
        if (logTracingProperties == null || logTracingProperties.needOutput("costTime")) {
            requestThreadLog.append(MessageFormat.format(requestLog("请求结束 " + request.getRequestURI() + REQUEST_END), System.currentTimeMillis() - startTime));
        }

        requestThreadLog.append("\n\n");
        LOG.info(requestThreadLog.toString());
        return result;
    }
}
