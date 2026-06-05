package io.github.snowylchen.util;

import cn.hutool.extra.spring.SpringUtil;
import io.github.snowylchen.syslog.ThirdPartyLogEvent;
import io.github.snowylchen.syslog.ThirdPartyLogInfo;
import io.github.snowylchen.trace.TraceContext;

import java.time.LocalDateTime;

/**
 * 静态日志辅助类
 * 供开发人员在代码中手动记录 SDK（如腾讯人脸识别 SDK 等）的 HTTP 请求响应日志
 *
 * @author chen
 */
public class HttpCallLogUtil {

    /**
     * 手动记录第三方 HTTP 调用日志
     *
     * @param url 请求地址
     * @param method 请求方法 (GET, POST等)
     * @param headers 请求头 (JSON 字符串)
     * @param requestBody 请求体
     * @param responseCode 响应状态码
     * @param responseBody 响应体
     * @param costTime 耗时 (毫秒)
     * @param success 是否成功
     * @param errorMessage 错误信息
     */
    public static void log(String url, String method, String headers, String requestBody,
                           Integer responseCode, String responseBody, Long costTime,
                           boolean success, String errorMessage) {
        try {
            ThirdPartyLogInfo logInfo = new ThirdPartyLogInfo();
            logInfo.setTraceId(TraceContext.currentTraceId());
            logInfo.setRequestUrl(url);
            logInfo.setRequestMethod(method);
            logInfo.setRequestHeaders(headers);
            logInfo.setRequestBody(requestBody);
            logInfo.setResponseCode(responseCode);
            logInfo.setResponseBody(responseBody);
            logInfo.setCostTime(costTime);
            logInfo.setSuccess(success);
            logInfo.setErrorMessage(errorMessage);
            logInfo.setCreateTime(LocalDateTime.now());

            SpringUtil.getApplicationContext().publishEvent(new ThirdPartyLogEvent(logInfo));
        } catch (Exception e) {
            // 忽略日志记录的异常，绝不阻塞主业务执行
        }
    }
}
