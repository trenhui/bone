package com.bone.iam.infrastructure.security;

import com.bone.iam.infrastructure.config.JwtConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService jwtTokenService;
    private final JwtConfig jwtConfig;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            JwtConfig jwtConfig,
            TokenBlacklistService tokenBlacklistService) {
        this.jwtTokenService = jwtTokenService;
        this.jwtConfig = jwtConfig;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String headerName = jwtConfig.getHeaderName();
        String authHeader = request.getHeader(headerName);

        if (authHeader != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            String rawToken = jwtTokenService.stripBearerToken(authHeader);
            if (tokenBlacklistService.isBlacklisted(rawToken)) {
                filterChain.doFilter(request, response);
                return;
            }
            jwtTokenService.parse(authHeader).ifPresent(principal -> {
                var granted =
                        principal.scopes().stream().map(SimpleGrantedAuthority::new).toList();
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal.username(), null, granted);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }
}

