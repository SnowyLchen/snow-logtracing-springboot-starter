package io.github.snowylchen.annotation;

/**
 * 操作类型常量类
 * <p>
 * 提供常用操作类型，使用者可以直接使用，也可以传入自定义的字符串
 * </p>
 *
 * @author chen
 */
public final class OperationType {

    public static final String QUERY = "QUERY";
    public static final String INSERT = "INSERT";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String IMPORT = "IMPORT";
    public static final String EXPORT = "EXPORT";
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String STARTUP = "STARTUP";
    public static final String SHUTDOWN = "SHUTDOWN";
    public static final String OTHER = "OTHER";

    private OperationType() {
        // Constants class
    }
}
