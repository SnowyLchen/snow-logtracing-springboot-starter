package io.github.snowylchen.trace;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 追踪 ID 生成器
 * 使用 ThreadLocalRandom 避免 UUID.randomUUID() 的同步锁竞争
 *
 * @author chen
 */
public final class TraceIdGenerator {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private TraceIdGenerator() {
    }

    /**
     * 生成 32 字符十六进制 TraceId
     */
    public static String generateTraceId() {
        return randomHex(32);
    }

    /**
     * 生成 8 字符十六进制 SpanId
     */
    public static String generateSpanId() {
        return randomHex(8);
    }

    private static String randomHex(int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = HEX_CHARS[random.nextInt(16)];
        }
        return new String(chars);
    }
}
