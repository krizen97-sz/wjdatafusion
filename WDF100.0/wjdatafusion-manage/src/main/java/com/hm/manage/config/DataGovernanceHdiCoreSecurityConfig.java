package com.hm.manage.config;

import com.hm.common.core.domain.model.LoginUser;
import com.hm.manage.service.governance.DataGovernanceHdiProperties;
import com.hm.manage.service.governance.hdi.*;
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
@ConditionalOnProperty(prefix = "data-governance-hdi-core", name = "enabled", havingValue = "true")
public class DataGovernanceHdiCoreSecurityConfig {
    public DataGovernanceHdiCoreSecurityConfig(DataGovernanceHdiProperties legacy) {
        if (legacy.isEnabled()) throw new IllegalStateException("Choose platform task core management; the original server proxy must be disabled");
    }
    @Bean @Order(0)
    public SecurityFilterChain hdiCoreChain(HttpSecurity http, HdiCoreSessions sessions) throws Exception {
        return http.securityMatcher("/detl", "/detl/**")
            .csrf(csrf -> csrf.disable()) // Native controller checks the actual platform session's CSRF for every mutation.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable()).headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, cause) -> HdiProtocol.failure(response, new HdiTaskFault(401, "请从平台进入数据流程"))))
            .addFilterBefore(new CoreSessionFilter(sessions), UsernamePasswordAuthenticationFilter.class).build();
    }
    public static final class CoreSessionFilter extends OncePerRequestFilter {
        private final HdiCoreSessions sessions;
        public CoreSessionFilter(HdiCoreSessions sessions) { this.sessions = sessions; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            try {
                sessions.authenticate(request);
                LoginUser user = (LoginUser) request.getAttribute(HdiCoreSessions.USER_ATTRIBUTE);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
                SecurityContextHolder.setContext(context); chain.doFilter(request, response);
            } catch (HdiTaskFault failure) { if (!response.isCommitted()) HdiProtocol.failure(response, failure); }
            finally { SecurityContextHolder.clearContext(); }
        }
    }
}
