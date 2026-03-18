package com.snow.logtracing.util;

import lombok.extern.slf4j.Slf4j;

import static com.snow.logtracing.util.LogUtil.requestLog;

/**
 * 执行时间工具
 *
 * @author chen
 * @date 2023/11/29
 */
@Slf4j
public class FunExecuteTimeUtil {

    @FunctionalInterface
    public interface CalculateTimeInterFace<T> {
        /**
         * 执行
         *
         * @return 执行结果
         */
        T execute() throws Throwable;
    }

    /**
     * 计算执行时间
     *
     * @param name          名字
     * @param calculateTime 计算时间
     * @return {@link T}
     */
    public static <T> T calculateTime(String name, CalculateTimeInterFace<T> calculateTime) throws Throwable {
        long start = System.currentTimeMillis();
        T result = calculateTime.execute();
        long end = System.currentTimeMillis();
        System.err.println(requestLog("\t【" + name + "】执行时间：" + (end - start) + "ms\t"));
        return result;
    }

    /**
     * 重载方法，用于不需要返回值的情况
     *
     * @param name
     * @param runnable
     */
    public static void calculateTime(String name, Runnable runnable) throws Throwable {
        long start = System.currentTimeMillis();
        runnable.run();
        long end = System.currentTimeMillis();
        System.err.println(requestLog("\t【" + name + "】执行时间：" + (end - start) + "ms\t"));
    }
}
