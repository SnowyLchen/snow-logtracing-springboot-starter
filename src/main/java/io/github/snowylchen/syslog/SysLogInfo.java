package io.github.snowylchen.syslog;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志信息模型
 *
 * @author chen
 */
@Data
public class SysLogInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志唯一 ID
     */
    private String logId;

    /**
     * 关联的 traceId（来自 TraceContext）
     */
    private String traceId;

    // ===== 操作描述 =====
    /**
     * 操作描述（注解 value 解析后的结果）
     */
    private String description;

    /**
     * 操作类型
     */
    private String operationType;

    /**
     * 业务模块
     */
    private String module;

    // ===== 方法信息 =====
    /**
     * 类名
     */
    private String className;

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 请求参数（JSON）
     */
    private String params;

    /**
     * 返回结果（JSON）
     */
    private String result;

    // ===== 请求信息 =====
    /**
     * 请求 URL
     */
    private String requestUrl;

    /**
     * 请求方法 (GET/POST/...)
     */
    private String requestMethod;

    /**
     * 客户端 IP
     */
    private String clientIp;

    /**
     * User-Agent
     */
    private String userAgent;

    // ===== 操作人信息 =====
    /**
     * 操作人 ID
     */
    private Object operatorId;

    /**
     * 操作人名称
     */
    private String operatorName;

    // ===== 执行信息 =====
    /**
     * 执行耗时（毫秒）
     */
    private Long costTime;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 异常信息
     */
    private String errorMessage;

    /**
     * 操作时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operateTime;
}
