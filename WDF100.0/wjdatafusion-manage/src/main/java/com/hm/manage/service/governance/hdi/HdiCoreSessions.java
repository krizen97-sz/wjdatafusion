package com.hm.manage.service.governance.hdi;

import com.hm.common.constant.CacheConstants;
import com.hm.common.constant.Constants;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.core.redis.RedisCache;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Browser transport for platform users; this never creates an HDI/BIC identity. */
@Component
public class HdiCoreSessions {
    public static final String COOKIE = "RYNEW_HDI_CORE_SESSION";
    public static final String SESSION_ATTRIBUTE = HdiCoreSessions.class.getName() + ".session";
    public static final String USER_ATTRIBUTE = HdiCoreSessions.class.getName() + ".user";
    public static final String CSRF_HEADER = "X-RYNEW-HDI-CSRF";
    public static final class Session {
        public final String opaque, csrf, loginUuid;
        public final long ownerId;
        public final HdiTaskStore.Workspace workspace;
        volatile long accessedAt;
        private Session(String opaque, String csrf, LoginUser user, HdiTaskStore.Workspace workspace) {
            this.opaque = opaque; this.csrf = csrf; loginUuid = user.getToken(); ownerId = user.getUserId(); this.workspace = workspace; accessedAt = System.currentTimeMillis();
        }
        @Override public String toString() { return "Platform task session [private]"; }
    }
    private final HdiCoreProperties properties;
    private final RedisCache cache;
    private final HdiTaskStore store;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> logins = new ConcurrentHashMap<>();
    public HdiCoreSessions(HdiCoreProperties properties, RedisCache cache, HdiTaskStore store) { this.properties = properties; this.cache = cache; this.store = store; }
    public synchronized Session create(LoginUser user, HttpServletRequest request, boolean renew) {
        if (user == null || user.getUserId() == null) throw new HdiTaskFault(401, "平台登录已失效");
        origin(request); LoginUser current = live(user.getToken(), user.getUserId()); require(current, "governance:flow:list");
        long now = System.currentTimeMillis();
        for (Session item : sessions.values()) if (now - item.accessedAt > ttl()) revoke(item);
        Session previous = sessions.get(logins.getOrDefault(current.getToken(), ""));
        if (previous != null && previous.ownerId == current.getUserId() && !renew) { previous.accessedAt = now; return previous; }
        if (sessions.size() >= 2048 && previous == null) throw new HdiTaskFault(503, "任务会话数量已达上限");
        Session session = new Session(secret(), secret(), current, store.workspace());
        if (previous != null) revoke(previous);
        sessions.put(session.opaque, session); logins.put(session.loginUuid, session.opaque); return session;
    }
    public Session authenticate(HttpServletRequest request) {
        origin(request); String opaque = null;
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) if (COOKIE.equals(cookie.getName())) {
            if (opaque != null) throw new HdiTaskFault(401, "任务会话无效，请从平台重新进入");
            opaque = cookie.getValue();
        }
        if (opaque == null || !opaque.matches("[A-Za-z0-9_-]{43}")) throw new HdiTaskFault(401, "请从平台进入数据流程");
        Session session = sessions.get(opaque);
        if (session == null) throw new HdiTaskFault(401, "任务会话已失效，请重新进入");
        if (System.currentTimeMillis() - session.accessedAt > ttl()) { revoke(session); throw new HdiTaskFault(401, "任务会话已过期"); }
        LoginUser user;
        try { user = live(session.loginUuid, session.ownerId); require(user, "governance:flow:list"); }
        catch (HdiTaskFault error) { if (error.status() != 503) revoke(session); throw error; }
        checkScopeHeader(request, "Workspace", Integer.toString(session.workspace.id()));
        checkScopeHeader(request, "projectCode", HdiTaskStore.project(session.ownerId));
        session.accessedAt = System.currentTimeMillis(); request.setAttribute(SESSION_ATTRIBUTE, session); request.setAttribute(USER_ATTRIBUTE, user);
        return session;
    }
    public void csrf(HttpServletRequest request, Session session) {
        var values = request.getHeaders(CSRF_HEADER); String value = values != null && values.hasMoreElements() ? values.nextElement() : null;
        if (value == null || values.hasMoreElements() || !MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8), session.csrf.getBytes(StandardCharsets.UTF_8))) throw new HdiTaskFault(403, "任务请求校验已失效，请保留草稿后重新进入");
    }
    public Map<String, Object> context(Session session, LoginUser current) {
        var user = current.getUser(); String display = user.getNickName() == null || user.getNickName().isBlank() ? user.getUserName() : user.getNickName();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", Map.of("id", Long.toString(current.getUserId()), "accountName", user.getUserName(), "userName", user.getUserName(), "name", display));
        result.put("workspaceId", Integer.toString(session.workspace.id()));
        result.put("workspaceName", session.workspace.name()); result.put("projectCode", HdiTaskStore.project(session.ownerId));
        result.put("projectName", "我的数据流程"); result.put("projectList", java.util.List.of(Map.of("projectCode", HdiTaskStore.project(session.ownerId), "projectName", "我的数据流程")));
        result.put("readOnly", !has(current, "governance:flow:edit")); result.put("locale", "zh_CN"); result.put("frameUrl", "/detl/task-host.html"); return result;
    }
    public LoginUser live(String uuid, long owner) {
        if (uuid == null || !uuid.matches("[A-Za-z0-9_-]{1,128}") || owner <= 0) throw new HdiTaskFault(401, "平台登录已失效");
        LoginUser user;
        try { user = cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + uuid); }
        catch (RuntimeException error) { throw new HdiTaskFault(503, "暂时无法核验平台登录"); }
        if (user == null || user.getUserId() == null || user.getUserId() != owner || !uuid.equals(user.getToken()) || user.getExpireTime() == null || user.getExpireTime() <= System.currentTimeMillis()) throw new HdiTaskFault(401, "平台登录已失效");
        return user;
    }
    public static boolean has(LoginUser user, String permission) { return user != null && user.getPermissions() != null && (user.getPermissions().contains(Constants.ALL_PERMISSION) || user.getPermissions().contains(permission)); }
    public static void require(LoginUser user, String permission) { if (!has(user, permission)) throw new HdiTaskFault(403, "没有该数据流程操作权限"); }
    private void checkScopeHeader(HttpServletRequest request, String name, String expected) {
        var values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) return;
        String value = values.nextElement();
        if (values.hasMoreElements() || !expected.equals(value)) throw new HdiTaskFault(403, "任务工作区与当前平台用户不匹配");
    }
    public void origin(HttpServletRequest request) {
        String expected = properties.getPublicOrigin(); URI uri;
        try { uri = URI.create(expected); }
        catch (RuntimeException error) { throw new HdiTaskFault(503, "平台任务入口尚未配置"); }
        if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getRawAuthority() == null || uri.getUserInfo() != null || !uri.getPath().isEmpty() || uri.getQuery() != null || uri.getFragment() != null) throw new HdiTaskFault(503, "平台任务入口配置无效");
        String origin = request.getHeader("Origin"), site = request.getHeader("Sec-Fetch-Site");
        if (origin != null) { if (!expected.equals(origin)) throw new HdiTaskFault(403, "任务请求来源无效"); return; }
        if ("same-origin".equals(site)) return;
        if (site != null && !"none".equals(site)) throw new HdiTaskFault(403, "任务请求来源无效");
        try { URI referer = URI.create(request.getHeader("Referer")); if (expected.equals(referer.getScheme() + "://" + referer.getRawAuthority()) && referer.getUserInfo() == null) return; }
        catch (RuntimeException ignored) { }
        throw new HdiTaskFault(403, "请从平台页面进入数据流程");
    }
    public boolean secureCookie() { return properties.getPublicOrigin().startsWith("https://"); }
    private long ttl() { return Math.max(60, Math.min(86400, properties.getSessionIdleSeconds())) * 1000L; }
    private synchronized void revoke(Session session) { sessions.remove(session.opaque, session); logins.remove(session.loginUuid, session.opaque); }
    private String secret() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
