package io.github.snowylchen.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级追踪注解，用于 Service/DAO 层方法的耗时追踪
 * <p>
 * 使用示例：
 * <pre>
 * {@code @Trace("查询用户列表")}
 * public List<User> listUsers() { ... }
 *
 * {@code @Trace}  // 默认使用 "类名#方法名"
 * public User findById(Long id) { ... }
 * </pre>
 *
 * @author chen
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Trace {

    /**
     * 自定义操作名，默认为空（使用 "类名#方法名"）
     */
    String value() default "";
}
