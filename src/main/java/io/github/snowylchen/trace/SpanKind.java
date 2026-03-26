package io.github.snowylchen.trace;

/**
 * Span 类型枚举
 *
 * @author chen
 */
public enum SpanKind {

    /**
     * 服务端处理（接收 HTTP 请求）
     */
    SERVER,

    /**
     * 客户端调用（发起 HTTP/DB/Redis 请求）
     */
    CLIENT,

    /**
     * 内部方法调用（Service/DAO 层）
     */
    INTERNAL
}
