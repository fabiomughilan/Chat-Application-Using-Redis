package com.freightfox.chatapp.config;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

/**
 * Parses Redis connection settings for Jedis.
 * <p>
 * Upstash's REST variables ({@code UPSTASH_REDIS_REST_URL} + {@code UPSTASH_REDIS_REST_TOKEN})
 * are HTTP credentials. This maps them to native Redis TLS on port 6379
 * ({@code rediss://default:<token>@host:6379}), which Spring Data Redis requires for pub/sub.
 */
final class RedisConnectionSettings {

    private RedisConnectionSettings() {
    }

    record ParsedRedisUrl(String host, int port, String username, String password, int database, boolean ssl) {
        boolean hasPassword() {
            return StringUtils.hasText(password);
        }
    }

    static ParsedRedisUrl resolve(String redisUrl, String restUrl, String restToken) {
        String primary = firstNonBlank(redisUrl, restUrl);
        if (!StringUtils.hasText(primary)) {
            throw new IllegalArgumentException(
                    "Set REDIS_URL (rediss://default:password@host:6379) "
                            + "or UPSTASH_REDIS_REST_URL + UPSTASH_REDIS_REST_TOKEN");
        }

        String trimmed = stripWrappingQuotes(primary.trim());
        URI uri = URI.create(trimmed);
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException("Redis URL is missing a host: " + redact(trimmed));
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("http".equals(scheme) || "https".equals(scheme)) {
            String token = firstNonBlank(extractPassword(uri), restToken);
            if (!StringUtils.hasText(token)) {
                throw new IllegalArgumentException(
                        "Upstash REST URL cannot be used alone. Add UPSTASH_REDIS_REST_TOKEN "
                                + "or set REDIS_URL to the rediss:// connection string from the Upstash console.");
            }
            return new ParsedRedisUrl(uri.getHost(), 6379, "default", stripWrappingQuotes(token.trim()), 0, true);
        }

        ParsedRedisUrl parsed = parseRedisUri(uri, trimmed);
        if (!parsed.hasPassword() && StringUtils.hasText(restToken) && isUpstash(parsed.host())) {
            return new ParsedRedisUrl(
                    parsed.host(),
                    parsed.port(),
                    parsed.username() != null ? parsed.username() : "default",
                    stripWrappingQuotes(restToken.trim()),
                    parsed.database(),
                    true);
        }
        if (isUpstash(parsed.host()) && !parsed.hasPassword()) {
            throw new IllegalArgumentException(
                    "Upstash Redis URL has no password. Use rediss://default:<token>@host:6379 "
                            + "or set UPSTASH_REDIS_REST_TOKEN.");
        }
        return parsed;
    }

    static ParsedRedisUrl parse(String redisUrl) {
        return resolve(redisUrl, null, null);
    }

    private static ParsedRedisUrl parseRedisUri(URI uri, String original) {
        int port = uri.getPort() > 0 ? uri.getPort() : 6379;
        boolean ssl = "rediss".equalsIgnoreCase(uri.getScheme()) || isUpstash(uri.getHost());

        String username = null;
        String password = extractPassword(uri);
        String userInfo = uri.getUserInfo();
        if (StringUtils.hasText(userInfo)) {
            int colon = userInfo.indexOf(':');
            if (colon > 0) {
                username = userInfo.substring(0, colon);
            } else if (colon < 0 && !StringUtils.hasText(password)) {
                password = userInfo;
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

        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException("Redis URL is missing a host: " + redact(original));
        }
        return new ParsedRedisUrl(uri.getHost(), port, username, password, database, ssl);
    }

    private static String extractPassword(URI uri) {
        String userInfo = uri.getUserInfo();
        if (!StringUtils.hasText(userInfo)) {
            return null;
        }
        int colon = userInfo.indexOf(':');
        if (colon < 0) {
            return userInfo;
        }
        String pass = userInfo.substring(colon + 1);
        return StringUtils.hasText(pass) ? pass : null;
    }

    private static boolean isUpstash(String host) {
        return StringUtils.hasText(host) && host.toLowerCase(Locale.ROOT).endsWith("upstash.io");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
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
