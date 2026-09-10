package com.freightfox.chatapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.freightfox.chatapp.pubsub.ChatMessageSubscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Slf4j
@Configuration
public class RedisConfig {

    public static final String CHATROOM_CHANNEL_PATTERN = "chatroom:*:channel";

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public JedisConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.url:}") String redisUrl,
            @Value("${UPSTASH_REDIS_REST_URL:}") String upstashRestUrl,
            @Value("${UPSTASH_REDIS_REST_TOKEN:}") String upstashRestToken,
            @Value("${spring.data.redis.timeout:10s}") Duration timeout) {
        RedisConnectionSettings.ParsedRedisUrl parsed =
                RedisConnectionSettings.resolve(redisUrl, upstashRestUrl, upstashRestToken);

        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(parsed.host(), parsed.port());
        standalone.setDatabase(parsed.database());
        if (parsed.username() != null) {
            standalone.setUsername(parsed.username());
        }
        if (parsed.password() != null) {
            standalone.setPassword(RedisPassword.of(parsed.password()));
        }

        JedisClientConfiguration.JedisClientConfigurationBuilder clientBuilder = JedisClientConfiguration.builder()
                .connectTimeout(timeout)
                .readTimeout(timeout);
        JedisClientConfiguration clientConfig = parsed.ssl()
                ? clientBuilder.useSsl().build()
                : clientBuilder.build();

        log.info(
                "Connecting to Redis with Jedis at {}:{} (tls={}, auth={})",
                parsed.host(),
                parsed.port(),
                parsed.ssl(),
                parsed.hasPassword() ? "password" : "none");

        return new JedisConnectionFactory(standalone, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // String keys and JSON values
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);

        // Hash keys and Hash values
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory connectionFactory,
            ChatMessageSubscriber chatMessageSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatMessageSubscriber, new PatternTopic(CHATROOM_CHANNEL_PATTERN));
        container.setRecoveryInterval(5000);
        return container;
    }
}
