package com.hm.manage.config;

import com.hm.common.core.domain.model.LoginUser;
import com.hm.manage.service.governance.DataGovernanceHdiGateway;
import com.hm.manage.service.governance.DataGovernanceHdiSessions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@ConditionalOnProperty(prefix = "data-governance-hdi", name = "enabled", havingValue = "true")
public class DataGovernanceHdiSecurityConfig {
    /** Separate chain prevents the platform's anonymous *.js/*.html rule from exposing original resources. */
    @Bean @Order(0)
    public SecurityFilterChain hdiTaskChain(HttpSecurity http, DataGovernanceHdiSessions sessions) throws Exception {
        return http.securityMatcher("/detl", "/detl/**")
            .csrf(csrf -> csrf.disable()) // Every request instead requires a validated origin plus live opaque session.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .exceptionHandling(error -> error.authenticationEntryPoint((request, response, cause) ->
                DataGovernanceHdiGateway.failure(response, new DataGovernanceHdiSessions.Failure(401, "请从平台进入数据集成"))))
            .addFilterBefore(new OpaqueSessionFilter(sessions), UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    /** Intentionally not a servlet Filter bean: it runs only inside the dedicated /detl chain. */
    public static final class OpaqueSessionFilter extends OncePerRequestFilter {
        private final DataGovernanceHdiSessions sessions;
        public OpaqueSessionFilter(DataGovernanceHdiSessions sessions) { this.sessions = sessions; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            try {
                sessions.authenticate(request);
                LoginUser user = (LoginUser) request.getAttribute(DataGovernanceHdiSessions.REQUEST_USER);
                var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
                var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                chain.doFilter(request, response);
            } catch (DataGovernanceHdiSessions.Failure error) {
                if (!response.isCommitted()) DataGovernanceHdiGateway.failure(response, error);
            } finally { SecurityContextHolder.clearContext(); }
        }
    }
}
