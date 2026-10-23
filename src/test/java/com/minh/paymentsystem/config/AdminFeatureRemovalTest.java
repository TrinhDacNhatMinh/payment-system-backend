package com.minh.paymentsystem.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.auth.security.jwt.JwtTokenProvider;
import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.repository.UserRepository;

@SpringBootTest(properties = {
        "spring.config.location=optional:classpath:/admin-removal-test.properties",
        "spring.datasource.url=jdbc:h2:mem:admin-removal;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=admin-removal-test-secret-at-least-32-bytes",
        "jwt.access-expiration=1800000",
        "jwt.refresh-expiration=604800000",
        "auth.allowed-origins=http://localhost:5173",
        "auth.refresh-token-cookie.secure=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminFeatureRemovalTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @Autowired
    private UserRepository users;
    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest
    @EnumSource(Role.class)
    void shouldReturnNotFoundForRemovedEndpointsWithValidAccessToken(Role role) throws Exception {
        User user = users.saveAndFlush(User.builder()
                .email("removed-endpoints-" + role.name() + "@example.com")
                .password("unused-test-password")
                .fullName("Endpoint Removal Test")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build());
        String authorization = "Bearer " + tokenProvider.generateAccessToken(user);

        mvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/transactions").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/admin/users/{id}/lock", user.getId()).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/admin/users/{id}/unlock", user.getId()).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound());

        assertThat(users.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldKeepRemovedEndpointsProtectedForUnauthenticatedRequests() throws Exception {
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/transactions")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/users/1/lock")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/users/1/unlock")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotExposeAdminPathsInOpenApi() throws Exception {
        String response = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var paths = objectMapper.readTree(response).path("paths");

        assertThat(paths.has("/api/v1/wallet/me")).isTrue();
        assertThat(paths.has("/api/v1/auth/login")).isTrue();
        paths.fieldNames().forEachRemaining(path -> assertThat(path).doesNotStartWith("/api/v1/admin"));
    }

    @Test
    void shouldStartWithoutSeedingAnAdministrator() {
        assertThat(users.count()).isZero();
        assertThat(users.existsByEmail("admin@paymentsystem.com")).isFalse();
    }
}
