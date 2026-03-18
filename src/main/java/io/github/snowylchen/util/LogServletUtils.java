package io.github.snowylchen.util;

import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @version 1.0.0
 * @className: LogServletUtils
 * @description: 日志Servlet工具类
 * @author: chen
 * @create: 2025/3/9 15:50
 */
public class LogServletUtils {
    /**
     * 获取当前 HttpServletRequest
     */
    public static HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

}
