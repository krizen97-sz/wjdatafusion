package com.hm.manage.service.governance.hdi;

import com.hm.common.constant.CacheConstants;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.core.redis.RedisCache;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HdiCoreSessionsTest {
    HdiCoreProperties properties; RedisCache cache; HdiTaskStore store; HdiCoreSessions sessions; LoginUser user;
    @BeforeEach void setUp() {
        properties = new HdiCoreProperties(); properties.setEnabled(true); properties.setPublicOrigin("https://platform.example");
        cache = mock(RedisCache.class); store = mock(HdiTaskStore.class); when(store.workspace()).thenReturn(new HdiTaskStore.Workspace(1, "平台任务域"));
        var account = new SysUser(); account.setUserId(7L); account.setUserName("editor"); account.setNickName("编辑用户");
        user = new LoginUser(7L, 1L, account, Set.of("governance:flow:list", "governance:flow:edit"));
        user.setToken("login-7"); user.setExpireTime(System.currentTimeMillis() + 300000);
        when(cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + "login-7")).thenAnswer(call -> user);
        sessions = new HdiCoreSessions(properties, cache, store);
    }
    MockHttpServletRequest request() { var r = new MockHttpServletRequest("GET", "/detl/_platform/context"); r.addHeader("Origin", properties.getPublicOrigin()); return r; }
    MockHttpServletRequest authenticated(HdiCoreSessions.Session session) { var r = request(); r.setCookies(new Cookie(HdiCoreSessions.COOKIE, session.opaque)); return r; }
    @Test void tabsReuseRealPlatformSessionAndOnlyExplicitRenewChangesCsrf() {
        var first = sessions.create(user, request(), false); var otherTab = sessions.create(user, request(), false);
        assertSame(first, otherTab); assertSame(first, sessions.authenticate(authenticated(first)));
        var renewed = sessions.create(user, request(), true); assertNotEquals(first.csrf, renewed.csrf);
        assertThrows(HdiTaskFault.class, () -> sessions.authenticate(authenticated(first)));
        assertEquals("rynew-user-7", sessions.context(renewed, user).get("projectCode"));
        assertFalse(sessions.context(renewed, user).containsKey("csrf")); assertTrue(sessions.secureCookie());
    }
    @Test void logoutAndPermissionRemovalInvalidateOpaqueSessionWithoutRefreshingLogin() {
        var session = sessions.create(user, request(), false); user.setPermissions(Set.of());
        assertEquals(403, assertThrows(HdiTaskFault.class, () -> sessions.authenticate(authenticated(session))).status());
        user.setPermissions(Set.of("governance:flow:list"));
        assertEquals(401, assertThrows(HdiTaskFault.class, () -> sessions.authenticate(authenticated(session))).status());
        var fresh = sessions.create(user, request(), false); user = null;
        assertEquals(401, assertThrows(HdiTaskFault.class, () -> sessions.authenticate(authenticated(fresh))).status());
        verify(cache, never()).setCacheObject(anyString(), any());
    }
    @Test void crossOriginAndForeignWorkspaceOrProjectAreRejectedBeforeTaskAccess() {
        var session = sessions.create(user, request(), false);
        for (String header : new String[]{"Workspace", "projectCode"}) {
            var r = authenticated(session); r.addHeader(header, "foreign");
            assertEquals(403, assertThrows(HdiTaskFault.class, () -> sessions.authenticate(r)).status());
        }
        var foreign = authenticated(session); foreign.removeHeader("Origin"); foreign.addHeader("Sec-Fetch-Site", "cross-site");
        assertEquals(403, assertThrows(HdiTaskFault.class, () -> sessions.authenticate(foreign)).status());
    }
    @Test void csrfIsSessionBoundAndViewerContextReflectsCurrentPlatformPermissions() {
        var session = sessions.create(user, request(), false); var r = authenticated(session);
        assertEquals(403, assertThrows(HdiTaskFault.class, () -> sessions.csrf(r, session)).status());
        r.addHeader(HdiCoreSessions.CSRF_HEADER, session.csrf); assertDoesNotThrow(() -> sessions.csrf(r, session));
        user.setPermissions(Set.of("governance:flow:list")); assertEquals(true, sessions.context(session, user).get("readOnly"));
        r.addHeader(HdiCoreSessions.CSRF_HEADER, session.csrf); assertThrows(HdiTaskFault.class, () -> sessions.csrf(r, session));
    }
}
