package io.github.snowylchen.syslog;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 第三方 HTTP 调用日志信息实体
 * 用于记录系统向外发起 HTTP 调用的全部报文明细，不掺杂任何业务字段
 *
 * @author chen
 */
@Data
public class ThirdPartyLogInfo {
    /**
     * 链路跟踪 ID，用于关联上下文请求
     */
    private String traceId;

    /**
     * 第三方请求 URL
     */
    private String requestUrl;

    /**
     * HTTP 请求方法 (GET, POST, PUT, DELETE 等)
     */
    private String requestMethod;

    /**
     * 请求头 (JSON 格式)
     */
    private String requestHeaders;

    /**
     * 请求体内容
     */
    private String requestBody;

    /**
     * HTTP 响应状态码 (如 200, 500)
     */
    private Integer responseCode;

    /**
     * 响应体内容
     */
    private String responseBody;

    /**
     * 调用耗时 (毫秒)
     */
    private Long costTime;

    /**
     * 是否调用成功 (true: 成功, false: 失败)
     */
    private Boolean success;

    /**
     * 异常堆栈或错误描述信息
     */
    private String errorMessage;

    /**
     * 记录创建时间
     */
    private LocalDateTime createTime;
}
