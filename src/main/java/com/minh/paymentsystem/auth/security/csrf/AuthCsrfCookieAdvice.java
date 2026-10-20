package com.minh.paymentsystem.auth.security.csrf;

import java.lang.reflect.Method;

import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.minh.paymentsystem.auth.controller.AuthController;
import com.minh.paymentsystem.common.response.BaseResponse;

@RestControllerAdvice(assignableTypes = AuthController.class)
@RequiredArgsConstructor
public class AuthCsrfCookieAdvice implements ResponseBodyAdvice<Object> {

    private final CookieCsrfTokenRepository csrfTokenRepository;

    @Override
    public boolean supports(@NonNull MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        Method method = returnType.getMethod();
        return method != null && ("login".equals(method.getName()) || "logout".equals(method.getName()));
    }

    @Override
    @Nullable
    public Object beforeBodyWrite(@Nullable Object body, @NonNull MethodParameter returnType, @NonNull MediaType selectedContentType,
            @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType, @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response) {
        if (body instanceof BaseResponse<?> result && result.isSuccess()
                && request instanceof ServletServerHttpRequest servletRequest
                && response instanceof ServletServerHttpResponse servletResponse) {
            // Controller-based login/logout do not invoke Spring Security's CSRF authentication/logout handlers.
            csrfTokenRepository.saveToken(null, servletRequest.getServletRequest(), servletResponse.getServletResponse());
        }
        return body;
    }
}
