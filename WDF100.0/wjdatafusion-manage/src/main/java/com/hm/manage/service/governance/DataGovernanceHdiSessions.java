package com.hm.manage.service.governance;

import com.hm.common.constant.CacheConstants;
import com.hm.common.constant.Constants;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.core.redis.RedisCache;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Opaque browser sessions always remain subordinate to the live platform login in Redis. */
@Component
public class DataGovernanceHdiSessions {
    public static final String COOKIE = "RYNEW_HDI_SESSION";
    public static final String REQUEST_SESSION = DataGovernanceHdiSessions.class.getName() + ".session";
    public static final String REQUEST_USER = DataGovernanceHdiSessions.class.getName() + ".user";
    private final DataGovernanceHdiProperties properties;
    private final DataGovernanceHdiClient client;
    private final RedisCache cache;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> byLogin = new LinkedHashMap<>();
    private final Object[] creationLocks = java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    public static final class Failure extends RuntimeException {
        private final int status;
        public Failure(int status, String safeMessage) { super(safeMessage); this.status = status; }
        public int status() { return status; }
    }

    public static final class Session {
        private final String opaque;
        private final String loginUuid;
        private final long userId;
        private final Map<String, Object> context;
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        private volatile long accessedAt;
        private volatile String csrfHeader = "X-CSRF-TOKEN";
        Session(String opaque, LoginUser user, Map<String, Object> context, long now) {
            this.opaque = opaque; loginUuid = user.getToken(); userId = user.getUserId();
            this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context)); accessedAt = now;
        }
        public String opaque() { return opaque; }
        public Map<String, Object> context() { return context; }
        public String frameUrl() {
            Object workspace = context.get("workspaceId");
            String value = workspace instanceof String || workspace instanceof Number ? workspace.toString() : "";
            try {
                if (!value.matches("[1-9][0-9]{0,18}") || Long.parseLong(value) <= 0) throw new NumberFormatException();
            } catch (NumberFormatException error) { throw new Failure(502, "原任务服务未映射有效工作区"); }
            return "/detl/#/task/taskList?appCode=" + value + "&flag=1&menuflag=1";
        }
        synchronized void initialCookies(URI target, DataGovernanceHdiClient.NativeSession nativeSession) throws IOException {
            if (!nativeSession.setCookies().isEmpty()) {
                updateCookies(target, Map.of("Set-Cookie", nativeSession.setCookies()));
            } else {
                for (String part : nativeSession.cookieHeader().split(";")) {
                    String[] pieces = part.trim().split("=", 2);
                    if (pieces.length != 2) throw new IOException("Invalid native cookie");
                    HttpCookie cookie = new HttpCookie(pieces[0], pieces[1]);
                    cookie.setVersion(0); cookie.setPath("/detl"); cookie.setDomain(target.getHost());
                    cookies.getCookieStore().add(target, cookie);
                }
            }
        }
        synchronized String cookieHeader(URI target) throws IOException {
            return String.join("; ", cookies.get(target, Map.of()).getOrDefault("Cookie", List.of()));
        }
        synchronized void updateCookies(URI target, Map<String, List<String>> headers) throws IOException {
            Map<String, List<String>> setCookies = new LinkedHashMap<>();
            for (var header : headers.entrySet()) if ("Set-Cookie".equalsIgnoreCase(header.getKey())) {
                if (header.getValue().size() > 16 || header.getValue().stream().anyMatch(v -> v.length() > 8192 || v.contains("\r") || v.contains("\n")))
                    throw new IOException("Native cookie limits exceeded");
                setCookies.put("Set-Cookie", header.getValue());
            }
            cookies.put(target, setCookies);
            if (cookies.getCookieStore().getCookies().size() > 32) throw new IOException("Native cookie count limit");
        }
        synchronized void clearCookies() { cookies.getCookieStore().removeAll(); }
        String csrfHeader() { return csrfHeader; }
        void csrfHeader(String name) {
            if (name != null && name.matches("(?i)X-[A-Z0-9-]*(?:CSRF|XSRF)[A-Z0-9-]*") && name.length() <= 80) csrfHeader = name;
        }
        @Override public String toString() { return "HDI session [private]"; }
    }

    @Autowired
    public DataGovernanceHdiSessions(DataGovernanceHdiProperties properties, DataGovernanceHdiClient client, RedisCache cache) {
        this(properties, client, cache, Clock.systemUTC());
    }
    DataGovernanceHdiSessions(DataGovernanceHdiProperties properties, DataGovernanceHdiClient client, RedisCache cache, Clock clock) {
        this.properties = properties; this.client = client; this.cache = cache; this.clock = clock;
    }

    public Session create(LoginUser principal, HttpServletRequest request) { return create(principal, request, false); }

    /** New tabs share genuine native CSRF/session state. Only an explicit reconnect rotates it. */
    public Session create(LoginUser principal, HttpServletRequest request, boolean renew) {
        checkOrigin(request);
        if (principal == null || principal.getUserId() == null) throw new Failure(401, "平台登录已失效");
        try {
            LoginUser verified = live(principal.getToken(), principal.getUserId());
            require(verified, "governance:flow:list");
            // Bounded lock striping prevents simultaneous tabs from creating competing native sessions.
            Object lock = creationLocks[Math.floorMod(verified.getToken().hashCode(), creationLocks.length)];
            synchronized (lock) {
                LoginUser current = live(verified.getToken(), verified.getUserId());
                require(current, "governance:flow:list");
                synchronized (byLogin) {
                    prune();
                    String opaque = byLogin.get(current.getToken());
                    Session existing = opaque == null ? null : sessions.get(opaque);
                    if (!renew && existing != null && existing.userId == current.getUserId()) {
                        existing.accessedAt = clock.millis();
                        return existing;
                    }
                }
                return createFresh(current);
            }
        } catch (Failure error) {
            if (error.status() == 401 || error.status() == 403) {
                synchronized (byLogin) {
                    String opaque = byLogin.get(principal.getToken());
                    Session existing = opaque == null ? null : sessions.get(opaque);
                    if (existing != null && existing.userId == principal.getUserId()) revoke(opaque);
                }
            }
            throw error;
        }
    }

    private Session createFresh(LoginUser current) {
        DataGovernanceHdiClient.NativeSession nativeSession;
        try { nativeSession = client.exchange(current.getUserId()); }
        catch (IOException | RuntimeException error) { throw new Failure(502, "原任务服务暂不能建立用户会话"); }
        LoginUser afterExchange = live(current.getToken(), current.getUserId());
        require(afterExchange, "governance:flow:list"); // Logout or revoked permission during exchange cannot mint a usable session.
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        Session session = new Session(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), current, nativeSession.context(), clock.millis());
        session.frameUrl(); // A real mapped native workspace is required before issuing any browser session.
        try { session.initialCookies(DataGovernanceHdiClient.localServiceUri(properties.getTaskServiceUrl()), nativeSession); }
        catch (IOException error) { throw new Failure(502, "原任务服务会话格式无效"); }
        synchronized (byLogin) {
            prune();
            if (sessions.size() >= 2048 && !byLogin.containsKey(current.getToken())) throw new Failure(503, "任务模块会话数量已达到限制");
            String previous = byLogin.put(current.getToken(), session.opaque);
            if (previous != null) revoke(previous);
            sessions.put(session.opaque, session);
        }
        return session;
    }

    public Session authenticate(HttpServletRequest request) {
        checkOrigin(request);
        String opaque = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) for (Cookie cookie : cookies) if (COOKIE.equals(cookie.getName())) {
            if (opaque != null) throw new Failure(401, "任务会话无效，请从平台重新进入");
            opaque = cookie.getValue();
        }
        if (opaque == null || !opaque.matches("[A-Za-z0-9_-]{43}")) throw new Failure(401, "请从平台进入数据集成");
        Session session = sessions.get(opaque);
        if (session == null || clock.millis() - session.accessedAt > ttlMillis()) {
            revoke(opaque); throw new Failure(401, "任务会话已过期，请从平台重新进入");
        }
        LoginUser current;
        try { current = live(session.loginUuid, session.userId); require(current, "governance:flow:list"); }
        catch (Failure failure) { if (failure.status() != 503) revoke(opaque); throw failure; }
        session.accessedAt = clock.millis();
        request.setAttribute(REQUEST_SESSION, session);
        request.setAttribute(REQUEST_USER, current);
        return session;
    }

    private LoginUser live(String uuid, long userId) {
        if (uuid == null || !uuid.matches("[A-Za-z0-9_-]{1,128}")) throw new Failure(401, "平台登录标识无效");
        LoginUser user;
        try { user = cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + uuid); }
        catch (RuntimeException error) { throw new Failure(503, "暂时无法核验平台登录"); }
        if (user == null || !Objects.equals(user.getUserId(), userId) || !uuid.equals(user.getToken())
                || user.getExpireTime() == null || user.getExpireTime() <= clock.millis())
            throw new Failure(401, "平台登录已失效");
        return user;
    }
    public static void require(LoginUser user, String permission) {
        if (user == null || user.getPermissions() == null || !(user.getPermissions().contains(Constants.ALL_PERMISSION)
                || user.getPermissions().contains(permission))) throw new Failure(403, "没有该数据集成操作权限");
    }
    public void revoke(String opaque) {
        if (opaque == null) return;
        Session removed = sessions.remove(opaque);
        if (removed != null) {
            removed.clearCookies();
            synchronized (byLogin) { byLogin.remove(removed.loginUuid, opaque); }
        }
    }
    private void prune() {
        long now = clock.millis(), ttl = ttlMillis();
        for (var entry : sessions.entrySet()) if (now - entry.getValue().accessedAt > ttl) revoke(entry.getKey());
    }
    private long ttlMillis() {
        int seconds = properties.getSessionIdleSeconds();
        if (seconds < 60 || seconds > 86400) throw new Failure(503, "任务会话配置无效");
        return seconds * 1000L;
    }
    public String publicOrigin() {
        if (!properties.isEnabled()) throw new Failure(503, "原任务模块尚未启用");
        return origin(properties.getPublicOrigin(), true);
    }
    public boolean secureCookie() { return publicOrigin().startsWith("https://"); }

    /** Browser Origin wins; absent Origin uses Fetch Metadata or an exact-origin Referer. */
    public void checkOrigin(HttpServletRequest request) {
        String expected = publicOrigin();
        String site = request.getHeader("Sec-Fetch-Site"), ref = request.getHeader("Referer"), supplied = request.getHeader("Origin");
        if ("cross-site".equalsIgnoreCase(site) || "same-site".equalsIgnoreCase(site)
                || ("none".equalsIgnoreCase(site) && (ref == null || ref.isBlank())))
            throw new Failure(403, "任务请求必须来自当前平台");
        if (supplied != null) {
            if (!expected.equals(origin(supplied, false))) throw new Failure(403, "任务请求来源不匹配");
            return;
        }
        if ("same-origin".equalsIgnoreCase(site)) return;
        if (ref != null && expected.equals(refererOrigin(ref))) return;
        throw new Failure(403, "请从当前平台页面进入任务模块");
    }
    private static String refererOrigin(String value) {
        try {
            URI uri = URI.create(value);
            return origin(uri.getScheme() + "://" + uri.getRawAuthority(), false);
        } catch (RuntimeException error) { return ""; }
    }
    private static String origin(String value, boolean configured) {
        try {
            URI uri = URI.create(value);
            if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath() == null || uri.getRawPath().isEmpty()))
                throw new IllegalArgumentException();
            int port = uri.getPort();
            String normalized = uri.getScheme() + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT);
            if (port != -1 && !(port == 80 && uri.getScheme().equals("http")) && !(port == 443 && uri.getScheme().equals("https"))) normalized += ":" + port;
            return normalized;
        } catch (RuntimeException error) {
            if (configured) throw new Failure(503, "平台对外地址配置无效");
            return "";
        }
    }
}
