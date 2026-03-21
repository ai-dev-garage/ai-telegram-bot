package com.ai.dev.garage.bot.adapter.in.rest;

import com.ai.dev.garage.bot.config.RunnerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class InternalApiAuthFilter extends OncePerRequestFilter {
    private static final String HEADER_NAME = "X-Runner-Token";
    private final RunnerProperties runnerProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String token = runnerProperties.getAuthToken();
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader(HEADER_NAME);
        if (!token.equals(header)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"unauthorized\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
