package com.hm.manage.service.governance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.hm.common.constant.CacheConstants;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.core.redis.RedisCache;
import com.hm.manage.config.DataGovernanceHdiSecurityConfig;
import com.hm.manage.controller.DataGovernanceHdiGatewayController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class DataGovernanceHdiSecurityConfigTest {
    @TempDir Path folder;
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({DataGovernanceHdiSecurityConfig.class, DataGovernanceHdiGatewayController.class, DataGovernanceHdiGateway.class, DataGovernanceHdiSessions.class, PlatformProbe.class})
    static class ConfigurationUnderTest {
        @Bean DataGovernanceHdiProperties hdiProperties() {
            var p = new DataGovernanceHdiProperties(); p.setEnabled(true); p.setPublicOrigin("http://localhost:18088"); return p;
        }
        @Bean RedisCache redisCache() { return mock(RedisCache.class); }
        @Bean RedisTemplate<Object, Object> redisTemplate() { return mock(RedisTemplate.class); }
        @Bean DataGovernanceHdiClient nativeClient() { return mock(DataGovernanceHdiClient.class); }
        @Bean @Order(1) SecurityFilterChain normalPlatform(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
        }
    }
    @RestController static class PlatformProbe {
        @GetMapping("/platform.js") String script() { return "ordinary-platform-resource"; }
    }
    AnnotationConfigWebApplicationContext context(boolean enabled) {
        var context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("hdi-test", Map.of("data-governance-hdi.enabled", Boolean.toString(enabled))));
        context.register(ConfigurationUnderTest.class); context.refresh(); return context;
    }
    MockMvc mvc(AnnotationConfigWebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @Test void originalJsAndHtmlNeverReachAnonymousPlatformFallbackAndLogoutRevokesThem() throws Exception {
        try (var context = context(true)) {
            var props = context.getBean(DataGovernanceHdiProperties.class);
            Path host = folder.toRealPath().resolve("host"); Files.createDirectories(host); Files.writeString(host.resolve("host.mjs"), "export const originalHost=true");
            props.setHostDir(host.toString());
            MockMvc mvc = mvc(context);
            mvc.perform(get("/platform.js")).andExpect(status().isOk());
            mvc.perform(get("/detl/host.mjs").header("Sec-Fetch-Site", "same-origin")).andExpect(status().isUnauthorized());
            mvc.perform(get("/detl/task-host.html").header("Sec-Fetch-Site", "same-origin")).andExpect(status().isUnauthorized());
            var user = new SysUser(); user.setUserId(9L); user.setUserName("viewer");
            var principal = new LoginUser(9L, 1L, user, Set.of("governance:flow:list")); principal.setToken("actual-platform-login"); principal.setExpireTime(System.currentTimeMillis() + 600000);
            RedisCache cache = context.getBean(RedisCache.class);
            when(cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + principal.getToken())).thenReturn(principal);
            when(context.getBean(DataGovernanceHdiClient.class).exchange(9L)).thenReturn(new DataGovernanceHdiClient.NativeSession("JSESSIONID=private-native", Map.of("user", Map.of("id", 9), "projectCode", "project", "projectList", List.of())));
            var req = new MockHttpServletRequest("POST", "/governance/hdi/session"); req.addHeader("Origin", props.getPublicOrigin());
            var session = context.getBean(DataGovernanceHdiSessions.class).create(principal, req);
            Cookie cookie = new Cookie(DataGovernanceHdiSessions.COOKIE, session.opaque());
            mvc.perform(get("/detl/host.mjs").header("Sec-Fetch-Site", "same-origin").cookie(cookie)).andExpect(status().isOk());
            when(cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + principal.getToken())).thenReturn(null);
            mvc.perform(get("/detl/host.mjs").header("Sec-Fetch-Site", "same-origin").cookie(cookie)).andExpect(status().isUnauthorized());
            mvc.perform(get("/platform.js")).andExpect(status().isOk());
        }
    }

    @Test void disabledFeatureDoesNotRegisterOriginalGatewayChainOrController() throws Exception {
        try (var context = context(false)) {
            assertFalse(context.containsBean("hdiTaskChain"));
            assertEquals(0, context.getBeansOfType(DataGovernanceHdiGatewayController.class).size());
            assertEquals(1, context.getBean(FilterChainProxy.class).getFilterChains().size());
            mvc(context).perform(get("/platform.js")).andExpect(status().isOk());
        }
    }
}
