package com.snow.logtracing.util;

import com.alibaba.fastjson2.JSON;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 日志工具类
 *
 * @author chen
 * @date 2024/02/15
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class LogUtil {
    public static final String UNKNOWN = "unknown";
    public static final String REQUEST_START = " 请求开始 ";
    public static final String REQUEST_END = " 总耗时：{0}ms ";
    public static final String BLUE_LOG = "\u001B[34m%s\u001B[0m";
    public static final String GREEN_LOG = "\u001B[36m%s\u001B[0m";

    /**
     * 构建日志字符串
     */
    public static String buildLog(Object... objArray) {
        StringBuilder logBuilder = new StringBuilder();

        for (Object obj : objArray) {
            if (logBuilder.length() > 0) {
                logBuilder.append(" \n ");
            }

            if (obj instanceof String) {
                logBuilder.append(obj);
            } else {
                logBuilder.append(JSON.toJSONString(obj));
            }
        }

        return logBuilder.toString();
    }

    /**
     * 日志格式化
     */
    public static String requestLog(String log) {
        // 组装一个一共50个字的日志，方便查看 传入一个字符串，前后均补上=
        int length = log.length();
        int maxLength = 100;
        int i = (maxLength - length) / 2;
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < i; j++) {
            sb.append("=");
        }
        sb.append(log);
        for (int j = 0; j < i; j++) {
            sb.append("=");
            // 如果是结束日志，增加换行
            if (j == i - 1 && log.equals(REQUEST_END)) {
                sb.append("\n\n");
            }
        }
        return sb.toString();
    }
}
