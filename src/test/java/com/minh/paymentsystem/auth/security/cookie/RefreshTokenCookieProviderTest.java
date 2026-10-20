package com.minh.paymentsystem.auth.security.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpCookie;
import java.time.Duration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RefreshTokenCookieProviderTest {

    @ParameterizedTest
    @ValueSource(booleans = {
            false,
            true
    })
    void shouldClearCookieWithTheSameScopeAndSecurityAttributes(boolean secure) {
        RefreshTokenCookieProvider provider = new RefreshTokenCookieProvider(secure);
        String createdHeader = provider.createCookie("test-refresh-token", Duration.ofHours(1));
        String clearedHeader = provider.clearCookie();
        HttpCookie created = HttpCookie.parse(createdHeader).get(0);
        HttpCookie cleared = HttpCookie.parse(clearedHeader).get(0);

        assertThat(created.getName()).isEqualTo(RefreshTokenCookieProvider.COOKIE_NAME);
        assertThat(cleared.getName()).isEqualTo(created.getName());
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(created.getMaxAge()).isEqualTo(3600);
        assertThat(created.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cleared.getPath()).isEqualTo(created.getPath());
        assertThat(created.getDomain()).isNull();
        assertThat(cleared.getDomain()).isNull();
        assertThat(created.isHttpOnly()).isTrue();
        assertThat(cleared.isHttpOnly()).isTrue();
        assertThat(created.getSecure()).isEqualTo(secure);
        assertThat(cleared.getSecure()).isEqualTo(secure);
        assertThat(createdHeader).contains("SameSite=Lax");
        assertThat(clearedHeader).contains("SameSite=Lax", "Max-Age=0");
    }
}
