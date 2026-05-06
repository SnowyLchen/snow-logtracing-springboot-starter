package io.github.snowylchen.syslog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SysLog SpEL 表达式解析器
 *
 * @author chen
 */
public class SysLogExpressionEvaluator {

    private static final Logger LOG = LoggerFactory.getLogger(SysLogExpressionEvaluator.class);

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * 解析带有 SpEL 表达式的字符串
     *
     * @param template 模板，如 "删除用户 #{#id}"
     * @param method   方法
     * @param args     参数
     * @return 解析后的字符串，解析失败返回原模板
     */
    public String evaluate(String template, Method method, Object[] args) {
        // 仅当模板包含 #{...} 时才触发 SpEL 解析，避免 "Order #123" 等普通字符串误触发
        if (template == null || !template.contains("#{")) {
            return template;
        }

        try {
            StandardEvaluationContext context = new MethodBasedEvaluationContext(
                    null, method, args, parameterNameDiscoverer);
            return parseTemplate(template, context);
        } catch (Exception e) {
            LOG.debug("[snow-logtracing] SpEL 表达式解析失败, template: {}", template, e);
            return template;
        }
    }

    /**
     * 手动解析 #{...} 模板
     */
    private String parseTemplate(String template, StandardEvaluationContext context) {
        StringBuilder result = new StringBuilder();
        int startIndex = 0;
        while (startIndex < template.length()) {
            int openIdx = template.indexOf("#{", startIndex);
            if (openIdx == -1) {
                result.append(template.substring(startIndex));
                break;
            }
            result.append(template, startIndex, openIdx);
            int closeIdx = template.indexOf("}", openIdx);
            if (closeIdx == -1) {
                result.append(template.substring(openIdx));
                break;
            }

            String expressionStr = template.substring(openIdx + 2, closeIdx);
            try {
                Expression expression = expressionCache.computeIfAbsent(expressionStr, parser::parseExpression);
                Object val = expression.getValue(context);
                result.append(val != null ? val.toString() : "null");
            } catch (Exception e) {
                LOG.debug("[snow-logtracing] 模板中 SpEL 表达式解析失败: {}", expressionStr, e);
                result.append("#{").append(expressionStr).append("}");
            }
            startIndex = closeIdx + 1;
        }
        return result.toString();
    }
}
