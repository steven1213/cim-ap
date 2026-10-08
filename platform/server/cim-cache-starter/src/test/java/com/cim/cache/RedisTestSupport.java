package com.cim.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 真集成测试用 Redis 支撑：连接工厂 / 序列化模板 / ObjectMapper 的统一构建，避免各测试重复。
 *
 * <p>地址可用系统属性覆盖：{@code -Dcim.test.redis.host=... -Dcim.test.redis.port=...}，
 * 默认 {@code 127.0.0.1:6379}（本机 Docker）。</p>
 */
public final class RedisTestSupport {

    public static final String HOST = System.getProperty("cim.test.redis.host", "127.0.0.1");
    public static final int PORT = Integer.getInteger("cim.test.redis.port", 6379);

    private RedisTestSupport() {
    }

    /** 新建连接工厂（调用方负责 {@code destroy()}）。 */
    public static LettuceConnectionFactory newConnectionFactory() {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(HOST, PORT);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(cfg);
        factory.afterPropertiesSet();
        return factory;
    }

    /** 探测 Redis 是否可达（用于 {@code Assumptions.assumeTrue} 跳过）。 */
    public static boolean reachable() {
        LettuceConnectionFactory factory = null;
        try {
            factory = newConnectionFactory();
            try (var conn = factory.getConnection()) {
                return "PONG".equalsIgnoreCase(conn.ping());
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (factory != null) {
                factory.destroy();
            }
        }
    }

    /** 与启动器一致的 L2 模板：String 键 + GenericJackson（带 @class 类型信息）。 */
    public static RedisTemplate<String, Object> newRedisTemplate(LettuceConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        RedisSerializer<String> keySer = RedisSerializer.string();
        template.setKeySerializer(keySer);
        template.setHashKeySerializer(keySer);
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    public static StringRedisTemplate newStringRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    public static ObjectMapper newObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        return om;
    }
}
