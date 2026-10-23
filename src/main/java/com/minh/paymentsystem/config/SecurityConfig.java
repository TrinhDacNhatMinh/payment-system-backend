package com.minh.paymentsystem.config;

import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.minh.paymentsystem.auth.security.AuthAccessDeniedHandler;
import com.minh.paymentsystem.auth.security.cors.AuthCorsProcessor;
import com.minh.paymentsystem.auth.security.csrf.AuthRequestFilter;
import com.minh.paymentsystem.auth.security.jwt.JwtAuthenticationEntryPoint;
import com.minh.paymentsystem.auth.security.jwt.JwtAuthenticationFilter;
import com.minh.paymentsystem.payment.security.PaymentCacheControlFilter;
import com.minh.paymentsystem.transfer.security.IdempotencyKey;
import com.minh.paymentsystem.transfer.security.TransferCacheControlFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final AuthAccessDeniedHandler authAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // MVC advice needs this bean while the JWT filter resolves handlerExceptionResolver.
    @Bean
    public static CookieCsrfTokenRepository csrfTokenRepository(@Value("${auth.refresh-token-cookie.secure:true}") boolean secure) {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        repository.setCookiePath("/api/v1/auth");
        repository.setHeaderName(AuthRequestFilter.CSRF_HEADER_NAME);
        repository.setCookieCustomizer(cookie -> cookie
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax"));
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository,
            @Value("${auth.allowed-origins:}") List<String> allowedOrigins) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(AuthRequestFilter.PROTECTED_REQUESTS))
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(authAccessDeniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/swagger-ui/**", "/v3/api-docs/**",
                                "/api/v1/payment/vnpay-return",
                                "/api/v1/payment/vnpay-webhook")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterAt(buildCorsFilter(allowedOrigins), CorsFilter.class)
                .addFilterBefore(new TransferCacheControlFilter(), CorsFilter.class)
                .addFilterBefore(new PaymentCacheControlFilter(), CorsFilter.class)
                .addFilterBefore(new AuthRequestFilter(Set.copyOf(allowedOrigins), authAccessDeniedHandler), CsrfFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsFilter buildCorsFilter(List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.AUTHORIZATION, AuthRequestFilter.CSRF_HEADER_NAME, IdempotencyKey.HEADER_NAME));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", configuration);
        CorsFilter filter = new CorsFilter(source);
        filter.setCorsProcessor(new AuthCorsProcessor(authAccessDeniedHandler));
        return filter;
    }
}
