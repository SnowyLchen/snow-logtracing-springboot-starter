package com.snow.logtracing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志追踪配置属性
 *
 * @author chen
 * @date 2024/03/18
 */
@Data
@Component
@ConfigurationProperties(prefix = "snow.logtracing")
public class LogTracingProperties {

    /**
     * 是否启用日志追踪，默认启用
     */
    private Boolean enable = true;

    /**
     * 需要输出的日志字段列表
     * 可选值：requestUrl(请求地址), methodInfo(类名方法), lineInfo(类名快捷跳转),
     * remoteIp(远程地址), headers(请求头信息), params(请求的参数),
     * response(返回的结果), costTime(耗时信息)
     */
    private List<String> fields = new ArrayList<>();

    /**
     * 自定义敏感信息脱敏字段，默认脱敏 password、token、secret
     */
    private List<String> sensitiveFields = new ArrayList<>();

    /**
     * 排除特定的 Header 信息，默认排除常见的无关 header
     */
    private List<String> excludeHeaders = new ArrayList<>();

    /**
     * 判断是否需要输出某个字段
     * 支持通配符 * 匹配
     */
    public boolean needOutput(String fieldName) {
        // 如果没有配置 fields，默认输出所有字段
        if (fields == null || fields.isEmpty()) {
            return true;
        }

        // 检查是否包含通配符 *
        for (String field : fields) {
            if ("*".equals(field)) {
                // 如果配置了 *，表示输出所有字段
                return true;
            }
            if (field != null && field.contains("*")) {
                // 支持部分通配，如 req* 匹配 requestUrl
                String pattern = field.replace("*", ".*");
                if (fieldName.matches(pattern)) {
                    return true;
                }
            }
            // 精确匹配
            if (field.equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取所有可用的字段
     */
    public List<String> getAvailableFields() {
        if (fields == null || fields.isEmpty()) {
            // 默认返回所有字段
            List<String> allFields = new ArrayList<>();
            allFields.add("requestUrl");
            allFields.add("methodInfo");
            allFields.add("lineInfo");
            allFields.add("remoteIp");
            allFields.add("headers");
            allFields.add("params");
            allFields.add("response");
            allFields.add("costTime");
            return allFields;
        }

        // 如果配置了通配符 *，返回所有字段
        if (fields.contains("*")) {
            List<String> allFields = new ArrayList<>();
            allFields.add("requestUrl");
            allFields.add("methodInfo");
            allFields.add("lineInfo");
            allFields.add("remoteIp");
            allFields.add("headers");
            allFields.add("params");
            allFields.add("response");
            allFields.add("costTime");
            return allFields;
        }

        return fields;
    }

    /**
     * 获取默认的敏感字段列表
     */
    public List<String> getDefaultSensitiveFields() {
        List<String> defaults = new ArrayList<>();
        defaults.add("password");
        defaults.add("token");
        defaults.add("secret");
        defaults.add("credentials");
        defaults.add("privateKey");
        return defaults;
    }

    /**
     * 获取最终的敏感字段列表（默认 + 自定义）
     */
    public List<String> getAllSensitiveFields() {
        List<String> allFields = getDefaultSensitiveFields();
        if (sensitiveFields != null && !sensitiveFields.isEmpty()) {
            for (String field : sensitiveFields) {
                if (!allFields.contains(field)) {
                    allFields.add(field);
                }
            }
        }
        return allFields;
    }

    /**
     * 获取默认的排除 Header 列表
     */
    public List<String> getDefaultExcludeHeaders() {
        List<String> defaults = new ArrayList<>();
        defaults.add("content-length");
        defaults.add("connection");
        defaults.add("accept");
        defaults.add("cache-control");
        defaults.add("accept-encoding");
        defaults.add("accept-language");
        defaults.add("upgrade-insecure-requests");
        return defaults;
    }

    /**
     * 获取最终的排除 Header 列表（默认 + 自定义）
     */
    public List<String> getAllExcludeHeaders() {
        List<String> allHeaders = getDefaultExcludeHeaders();
        if (excludeHeaders != null && !excludeHeaders.isEmpty()) {
            for (String header : excludeHeaders) {
                if (!allHeaders.contains(header.toLowerCase())) {
                    allHeaders.add(header.toLowerCase());
                }
            }
        }
        return allHeaders;
    }
}
