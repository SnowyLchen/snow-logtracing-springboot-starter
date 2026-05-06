package io.github.snowylchen.annotation;

import java.lang.annotation.*;

/**
 * 系统操作日志注解
 * <p>
 * 可标注在类或方法上。方法级别配置覆盖类级别配置。
 * </p>
 *
 * @author chen
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SysLog {

    /**
     * 操作描述，支持 SpEL 表达式（以 # 开头时解析为 SpEL）
     * <p>示例：@SysLog("删除用户 #{#id}")</p>
     */
    String value() default "";

    /**
     * 操作类型
     * <p>建议使用 {@link OperationType} 中的常量，也支持自定义字符串</p>
     */
    String type() default OperationType.OTHER;

    /**
     * 所属业务模块
     */
    String module() default "";

    /**
     * 是否记录请求参数
     */
    boolean saveParams() default true;

    /**
     * 是否记录返回结果
     */
    boolean saveResult() default true;
}
