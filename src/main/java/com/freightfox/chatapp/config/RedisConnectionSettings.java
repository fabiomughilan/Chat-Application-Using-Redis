package com.freightfox.chatapp.config;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

/**
 * Parses {@code REDIS_URL} / {@code spring.data.redis.url} for Lettuce.
 * Upstash requires TLS; a {@code redis://} URL to {@code *.upstash.io} is treated as {@code rediss://}.
 */
final class RedisConnectionSettings {

    private RedisConnectionSettings() {
    }

    record ParsedRedisUrl(String host, int port, String username, String password, int database, boolean ssl) {
    }

    static ParsedRedisUrl parse(String redisUrl) {
        if (!StringUtils.hasText(redisUrl)) {
            throw new IllegalArgumentException("spring.data.redis.url / REDIS_URL is not set");
        }

        String trimmed = stripWrappingQuotes(redisUrl.trim());
        URI uri = URI.create(trimmed);
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException("Redis URL is missing a host: " + redact(trimmed));
        }

        int port = uri.getPort() > 0 ? uri.getPort() : 6379;
        boolean ssl = "rediss".equalsIgnoreCase(uri.getScheme())
                || uri.getHost().toLowerCase(Locale.ROOT).endsWith("upstash.io");

        String username = null;
        String password = null;
        String userInfo = uri.getUserInfo();
        if (StringUtils.hasText(userInfo)) {
            int colon = userInfo.indexOf(':');
            if (colon < 0) {
                password = userInfo;
            } else {
                String user = userInfo.substring(0, colon);
                String pass = userInfo.substring(colon + 1);
                if (StringUtils.hasText(user)) {
                    username = user;
                }
                if (StringUtils.hasText(pass)) {
                    password = pass;
                }
            }
        }

        int database = 0;
        String path = uri.getPath();
        if (StringUtils.hasText(path) && path.length() > 1) {
            try {
                database = Integer.parseInt(path.substring(1).split("/")[0]);
            } catch (NumberFormatException ignored) {
                database = 0;
            }
        }

        return new ParsedRedisUrl(uri.getHost(), port, username, password, database, ssl);
    }

    static String redact(String redisUrl) {
        if (!StringUtils.hasText(redisUrl)) {
            return "";
        }
        return redisUrl.replaceAll("://([^:/@]+):[^@]+@", "://$1:***@");
    }

    private static String stripWrappingQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
