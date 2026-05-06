package io.github.snowylchen.syslog;

/**
 * 操作人信息提供者 SPI
 * <p>
 * 使用者实现此接口并注册为 Spring Bean，@SysLog 切面会自动调用获取当前操作人信息。
 * </p>
 *
 * @author chen
 */
public interface OperatorProvider<T> {

    /**
     * 获取当前操作人 ID
     *
     * @return 操作人 ID
     */
    T getOperatorId();

    /**
     * 获取当前操作人名称
     *
     * @return 操作人名称
     */
    String getOperatorName();
}
