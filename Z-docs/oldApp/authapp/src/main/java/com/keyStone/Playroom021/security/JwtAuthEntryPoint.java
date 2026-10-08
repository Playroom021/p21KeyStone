package com.keyStone.Playroom021.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyStone.Playroom021.dto.ApiError;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException, ServletException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        ApiError error = ApiError.builder()
                .status(HttpServletResponse.SC_UNAUTHORIZED)
                .message("Authentication required or token invalid/expired")
                .build();
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
