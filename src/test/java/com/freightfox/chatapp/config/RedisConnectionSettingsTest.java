package com.freightfox.chatapp.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisConnectionSettingsTest {

    @Test
    void localRedisUrlDoesNotEnableSsl() {
        var parsed = RedisConnectionSettings.parse("redis://redis:6379");
        assertThat(parsed.host()).isEqualTo("redis");
        assertThat(parsed.port()).isEqualTo(6379);
        assertThat(parsed.ssl()).isFalse();
        assertThat(parsed.username()).isNull();
        assertThat(parsed.password()).isNull();
    }

    @Test
    void upstashRedisSchemeIsUpgradedToTls() {
        var parsed = RedisConnectionSettings.parse(
                "redis://default:secret@pet-mole-114407.upstash.io:6379");
        assertThat(parsed.host()).isEqualTo("pet-mole-114407.upstash.io");
        assertThat(parsed.port()).isEqualTo(6379);
        assertThat(parsed.ssl()).isTrue();
        assertThat(parsed.username()).isEqualTo("default");
        assertThat(parsed.password()).isEqualTo("secret");
    }

    @Test
    void redissUrlKeepsTls() {
        var parsed = RedisConnectionSettings.parse(
                "rediss://default:secret@pet-mole-114407.upstash.io:6379");
        assertThat(parsed.ssl()).isTrue();
    }

    @Test
    void quotedUrlAndDatabasePathAreParsed() {
        var parsed = RedisConnectionSettings.parse("\"redis://localhost:6380/2\"");
        assertThat(parsed.host()).isEqualTo("localhost");
        assertThat(parsed.port()).isEqualTo(6380);
        assertThat(parsed.database()).isEqualTo(2);
        assertThat(parsed.ssl()).isFalse();
    }

    @Test
    void redactHidesPassword() {
        assertThat(RedisConnectionSettings.redact("rediss://default:super-secret@host:6379"))
                .isEqualTo("rediss://default:***@host:6379");
    }

    @Test
    void blankUrlIsRejected() {
        assertThatThrownBy(() -> RedisConnectionSettings.parse("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restUrlAndTokenBecomeNativeTlsRedis() {
        var parsed = RedisConnectionSettings.resolve(
                null,
                "https://pet-mole-114407.upstash.io",
                "rest-token");
        assertThat(parsed.host()).isEqualTo("pet-mole-114407.upstash.io");
        assertThat(parsed.port()).isEqualTo(6379);
        assertThat(parsed.ssl()).isTrue();
        assertThat(parsed.username()).isEqualTo("default");
        assertThat(parsed.password()).isEqualTo("rest-token");
    }

    @Test
    void httpsRedisUrlUsesRestTokenForPassword() {
        var parsed = RedisConnectionSettings.resolve(
                "https://pet-mole-114407.upstash.io",
                null,
                "rest-token");
        assertThat(parsed.ssl()).isTrue();
        assertThat(parsed.password()).isEqualTo("rest-token");
        assertThat(parsed.port()).isEqualTo(6379);
    }

    @Test
    void quotedRestUrlIsAccepted() {
        var parsed = RedisConnectionSettings.resolve(
                null,
                "\"https://pet-mole-114407.upstash.io\"",
                "\"rest-token\"");
        assertThat(parsed.host()).isEqualTo("pet-mole-114407.upstash.io");
        assertThat(parsed.password()).isEqualTo("rest-token");
    }

    @Test
    void restUrlWithoutTokenIsRejected() {
        assertThatThrownBy(() -> RedisConnectionSettings.resolve(
                "https://pet-mole-114407.upstash.io", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REST URL cannot be used alone");
    }
}
