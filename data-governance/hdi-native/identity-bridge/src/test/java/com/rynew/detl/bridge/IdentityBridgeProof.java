package com.rynew.detl.bridge;

import com.hikvision.idatafusion.detl.dto.user.UserDetails;
import com.hikvision.idatafusion.detl.mapper.*;
import com.hikvision.idatafusion.detl.model.EtlFlow;
import com.hikvision.idatafusion.detl.utils.UserSessionUtils;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.*;
import javax.servlet.http.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/** Executable with only the original SDK and JDK; invokes the original vendor session helper. */
public final class IdentityBridgeProof {
    static int checks;
    static final long NOW = 1_700_000_000_000L;
    static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    static final byte[] KEY = new byte[32], BODY = "{\"subject\":\"rynew:user:7\"}".getBytes(StandardCharsets.UTF_8);
    static { for (int i = 0; i < KEY.length; i++) KEY[i] = (byte)i; }
    static String nonce(int seed) { byte[] bytes = new byte[32]; bytes[0] = (byte)seed; return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    interface Checked { void run() throws Exception; }
    static void denied(Checked action, String label) throws Exception { try { action.run(); throw new AssertionError(label); } catch (SecurityException | IllegalArgumentException | IOException expected) { checks++; } }
    static Path child(Path root, String name) throws IOException { return PrivateFiles.replayDirectory(root.resolve(name).toString()); }
    static byte[] mapping() {
        String json = "{\"schemaVersion\":1,\"users\":[{\"subject\":\"rynew:user:7\",\"userId\":73,\"userName\":\"原用户\",\"accountName\":\"native-user\",\"realUserName\":\"真实映射用户\",\"roleType\":5,\"tenantCode\":\"tenant-a\",\"workspaceId\":901,\"projectCode\":\"project-a\",\"projectName\":\"项目甲\",\"language\":\"zh_CN\",\"projectList\":[{\"projectCode\":\"project-a\",\"projectName\":\"项目甲\"}]}]}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
    static TicketVerifier verifier(Path path) { return new TicketVerifier(KEY, path, CLOCK); }
    static String signature(String method, String uri, long timestamp, String nonce, byte[] body) { return TicketVerifier.sign(KEY, method, uri, Long.toString(timestamp), nonce, body); }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("rynew-bridge-proof-").toRealPath();
        try {
            cryptoAndReplay(root); mappingRules(); privateFileRules(root); nativeSessionAndFilter(root); flowScopeAndFilter(root); NativeSaveAuthorizationProof.run(root); defaultDisabled(); enabledConfiguration(root);
            System.out.println("IdentityBridgeProof: " + checks + " checks passed (real original session API; no server, database, license or native initialization)");
        } finally { try (var paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) Files.deleteIfExists(path); } }
    }
    static void cryptoAndReplay(Path root) throws Exception {
        String n = nonce(0), ts = Long.toString(NOW), path = "/detl/_platform/session";
        String signed = signature("POST", path, NOW, n, BODY);
        check(signed.equals("d67c02c36fb175b05f8bd31abf2b66512b8b7d639fcc0f887e1b42c813b08ca3"), "independent Python HMAC vector");
        Path replay = child(root, "replay"); TicketVerifier verifier = verifier(replay);
        long expiry = verifier.verify("POST", path, BODY, ts, n, signed);
        denied(() -> verifier.verify("GET", path, BODY, ts, n, signed), "method binding");
        denied(() -> verifier.verify("POST", path + "/other", BODY, ts, n, signed), "path binding");
        denied(() -> verifier.verify("POST", path, "{\"subject\":\"rynew:user:8\"}".getBytes(StandardCharsets.UTF_8), ts, n, signed), "body binding");
        denied(() -> verifier.verify("POST", path, BODY, ts, nonce(1), signed), "nonce binding");
        denied(() -> verifier.verify("POST", path, BODY, ts, n, signed.toUpperCase(Locale.ROOT)), "strict signature encoding");
        for (long issued : new long[]{ NOW - 60_001, NOW + 5_001 }) denied(() -> verifier.verify("POST", path, BODY, Long.toString(issued), n, signature("POST", path, issued, n, BODY)), "time window");
        verifier.consume(n, expiry);
        denied(() -> verifier.consume(n, expiry), "same-process replay");
        denied(() -> verifier(replay).consume(n, expiry), "restart replay");
        byte[] different = KEY.clone(); different[0] ^= 1;
        denied(() -> new TicketVerifier(different, replay, CLOCK).verify("POST", path, BODY, ts, n, signed), "wrong signer");
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1); AtomicInteger success = new AtomicInteger(), refused = new AtomicInteger();
        Runnable racing = () -> { try { ready.countDown(); start.await(); verifier(replay).consume(nonce(3), expiry); success.incrementAndGet(); } catch (SecurityException expected) { refused.incrementAndGet(); } catch (Exception failure) { throw new RuntimeException(failure); } };
        Thread a = new Thread(racing), b = new Thread(racing); a.start(); b.start(); check(ready.await(2, TimeUnit.SECONDS), "race ready"); start.countDown(); a.join(); b.join();
        check(success.get() == 1 && refused.get() == 1, "atomic cross-instance nonce claim");
    }
    static void mappingRules() throws Exception {
        IdentityMappings mappings = new IdentityMappings(mapping()); IdentityMappings.Identity identity = mappings.resolve(BODY);
        check(identity.userId == 73 && identity.roleType == 5 && identity.workspaceId == 901, "explicit mapped non-admin identity");
        IdentityMappings.Identity english = new IdentityMappings(new String(mapping(), StandardCharsets.UTF_8).replace("zh_CN", "en_US").getBytes(StandardCharsets.UTF_8)).resolve(BODY);
        check(english.response().get("locale").equals("en_US") && english.response().get("workspaceId").equals("901"), "host receives explicit language and workspace");
        var largeMapping = IdentityMappings.JSON.readTree(mapping());
        var largeUser = (com.fasterxml.jackson.databind.node.ObjectNode)largeMapping.path("users").get(0);
        largeUser.put("userId", 9_007_199_254_740_993L); largeUser.put("workspaceId", 9_007_199_254_740_993L);
        var largeIdentity = new IdentityMappings(IdentityMappings.JSON.writeValueAsBytes(largeMapping)).resolve(BODY);
        var serialized = IdentityMappings.JSON.readTree(IdentityMappings.JSON.writeValueAsBytes(largeIdentity.response()));
        for (var id : List.of(serialized.path("workspaceId"), serialized.path("user").path("id"), serialized.path("user").path("userId")))
            check(id.isTextual() && id.textValue().equals("9007199254740993"), "browser context preserves long id as exact decimal string");
        check(largeIdentity.userId == 9_007_199_254_740_993L && largeIdentity.workspaceId == 9_007_199_254_740_993L, "native identity retains exact long values");
        denied(() -> new IdentityMappings(twoMappings(true, false, true)), "two subjects cannot share a native user id in one tenant");
        denied(() -> new IdentityMappings(twoMappings(false, true, true)), "two subjects cannot share a native account in one tenant");
        check(new IdentityMappings(twoMappings(false, false, true)).resolve("{\"subject\":\"rynew:user:8\"}".getBytes(StandardCharsets.UTF_8)).userId == 74, "distinct native identities are accepted");
        check(new IdentityMappings(twoMappings(true, true, false)).resolve("{\"subject\":\"rynew:user:8\"}".getBytes(StandardCharsets.UTF_8)).tenantCode.equals("tenant-b"), "native uniqueness is scoped to each tenant");
        for (String body : List.of("{\"subject\":\"rynew:user:8\"}", "{\"subject\":\"rynew:user:7\",\"roleType\":1}", "{\"subject\":\"rynew:user:7\",\"subject\":\"rynew:user:8\"}", "{\"subject\":\"rynew:user:7\"}{}")) denied(() -> mappings.resolve(body.getBytes(StandardCharsets.UTF_8)), "unknown identity or forged payload");
        denied(() -> mappings.resolve(new byte[]{(byte)0xc3, (byte)0x28}), "malformed UTF8");
        denied(() -> new IdentityMappings(new String(mapping(), StandardCharsets.UTF_8).replace("\"roleType\":5,", "").getBytes(StandardCharsets.UTF_8)), "no role default");
        denied(() -> new IdentityMappings(new String(mapping(), StandardCharsets.UTF_8).replace("\"roleType\":5", "\"roleType\":0").getBytes(StandardCharsets.UTF_8)), "unknown role rejected");
        IdentityMappings empty = new IdentityMappings("{\"schemaVersion\":1,\"users\":[]}".getBytes(StandardCharsets.UTF_8));
        denied(() -> empty.resolve(BODY), "empty map never grants admin");
    }
    static byte[] twoMappings(boolean sameId, boolean sameAccount, boolean sameTenant) throws Exception {
        var document = IdentityMappings.JSON.readTree(mapping());
        var users = (com.fasterxml.jackson.databind.node.ArrayNode)document.path("users");
        var second = ((com.fasterxml.jackson.databind.node.ObjectNode)users.get(0)).deepCopy();
        second.put("subject", "rynew:user:8");
        if (!sameId) second.put("userId", 74);
        if (!sameAccount) second.put("accountName", "another-native-user");
        if (!sameTenant) second.put("tenantCode", "tenant-b");
        users.add(second); return IdentityMappings.JSON.writeValueAsBytes(document);
    }
    static void privateFileRules(Path root) throws Exception {
        Path key = root.resolve("key"); Files.write(key, KEY); Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-------"));
        check(Arrays.equals(KEY, PrivateFiles.read(key.toString(), 64)), "raw private key read");
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-r--r--"));
        denied(() -> PrivateFiles.read(key.toString(), 64), "public mode rejected"); check(Files.getPosixFilePermissions(key).contains(java.nio.file.attribute.PosixFilePermission.OTHERS_READ), "never chmod unknown configuration");
        Path link = root.resolve("link"); Files.createSymbolicLink(link, key); denied(() -> PrivateFiles.read(link.toString(), 64), "symlink rejected");
        Path unrelated = root.resolve("unrelated"); Files.createDirectory(unrelated, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))); Path file = unrelated.resolve("keep.txt"); Files.writeString(file, "unchanged");
        denied(() -> PrivateFiles.replayDirectory(unrelated.toString()), "unowned nonempty directory rejected"); check(Files.readString(file).equals("unchanged"), "unrelated file retained");
    }
    static void nativeSessionAndFilter(Path root) throws Exception {
        IdentityBridgeFilter filter = filter(verifier(child(root, "http-replays")), flowMapper(Map.of(), new AtomicInteger(), false));
        Request request = new Request(BODY, nonce(20)); Response response = new Response(); Session previousSession = request.session = new Session(); previousSession.attrs.put("untrusted-old-role", "must disappear");
        Request previousRequest = new Request(BODY, nonce(40)); ServletRequestAttributes previousContext = new ServletRequestAttributes(previousRequest.proxy()); RequestContextHolder.setRequestAttributes(previousContext);
        filter.doFilter(request.proxy(), response.proxy(), (a,b) -> { throw new AssertionError("exchange entered ordinary task chain"); });
        check(response.status == 200, "native exchange success"); check(previousSession.invalid && request.session != previousSession, "session fixation prevented");
        check(request.session.seconds == 900, "bounded native session");
        String nativeJson = (String)request.session.attrs.get("_const_etl_role_info_");
        UserDetails nativeDetails = com.alibaba.fastjson.JSON.parseObject(nativeJson, UserDetails.class);
        check(nativeDetails.getUserRole().getUserId() == 73 && nativeDetails.getUserRole().getUserRoleType() == 5, "real vendor helper wrote mapped UserRole");
        check(nativeDetails.getUserRole().getWorkspace() == 901 && nativeDetails.getProjectCode().equals("project-a"), "native workspace and project preserved");
        check(RequestContextHolder.getRequestAttributes() == previousContext, "request context restored"); RequestContextHolder.resetRequestAttributes();
        check(IdentityMappings.JSON.readTree(response.text.toString()).path("data").path("user").path("id").asLong() == 73, "real response display identity");
        check("no-store".equals(response.headers.get("Cache-Control")), "noncacheable response");
        Response replay = new Response(); filter.doFilter(request.proxy(), replay.proxy(), (a,b) -> {}); check(replay.status == 401, "HTTP replay denied");
        for (String mode : List.of("remote", "browser", "query", "duplicate", "forged", "oversized")) {
            Request rejected = new Request(BODY, nonce(30 + mode.length()));
            if (mode.equals("remote")) rejected.address = "192.0.2.10";
            if (mode.equals("browser")) rejected.headers.put("Origin", List.of("https://example.invalid"));
            if (mode.equals("query")) rejected.query = "next=other";
            if (mode.equals("duplicate")) rejected.headers.put("X-RYNEW-Timestamp", List.of(Long.toString(NOW), Long.toString(NOW)));
            if (mode.equals("forged")) rejected.body = "{\"subject\":\"rynew:user:8\"}".getBytes(StandardCharsets.UTF_8);
            if (mode.equals("oversized")) rejected.body = new byte[4097];
            Response result = new Response(); filter.doFilter(rejected.proxy(), result.proxy(), (a,b) -> {});
            check(result.status == 401 && rejected.session == null, "HTTP failure before session: " + mode);
        }
        for (byte[] invalidBody : List.of("{\"subject\":\"rynew:user:8\"}".getBytes(StandardCharsets.UTF_8), "{\"subject\":\"rynew:user:7\",\"roleType\":1}".getBytes(StandardCharsets.UTF_8), new byte[]{(byte)0xc3,(byte)0x28})) {
            Request invalid = new Request(invalidBody, nonce(55)); Response result = new Response(); filter.doFilter(invalid.proxy(), result.proxy(), (a,b) -> {});
            check(result.status == 401 && invalid.session == null, "correctly signed unknown/forged/malformed identity is refused");
        }
        Request ordinary = new Request(BODY, nonce(50)); ordinary.servletPath = "/flow/findFlowPage"; ordinary.method = "GET"; Response passthrough = new Response(); AtomicInteger chained = new AtomicInteger();
        filter.doFilter(ordinary.proxy(), passthrough.proxy(), (a,b) -> chained.incrementAndGet()); check(chained.get() == 1 && passthrough.text.toString().isEmpty(), "normal original filters preserved");
    }
    static EtlFlow flow(long id, long groupId) {
        EtlFlow flow = new EtlFlow(); flow.setId(id); flow.setGroupId(groupId); return flow;
    }
    static <T> T emptyMapper(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("toString")) return "UnusedOriginalMapperProof";
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            throw new AssertionError("This mapper must not be called by the current proof");
        }));
    }
    static IdentityBridgeFilter filter(TicketVerifier verifier, EtlFlowMapper mapper) {
        NativeFlowAuthorizer scope = new NativeFlowAuthorizer(mapper, emptyMapper(EtlStreamDao.class), emptyMapper(EtlStepDao.class), emptyMapper(EtlStepItemDao.class), emptyMapper(EtlModuleDao.class), emptyMapper(EtlDstTableDao.class));
        NativeSaveAuthorizer save = new NativeSaveAuthorizer(true, scope, emptyMapper(WorkspaceMapper.class), emptyMapper(SysFolderMapper.class), emptyMapper(EtlInstanceDao.class), emptyMapper(EtlGroupAdapterDao.class), List::of);
        return new IdentityBridgeFilter(verifier, new IdentityMappings(mapping()), new NativeSessionIssuer(900), scope, save);
    }
    static void registerOtherMappers(AnnotationConfigApplicationContext context) {
        context.registerBean(EtlStreamDao.class, () -> emptyMapper(EtlStreamDao.class));
        context.registerBean(EtlStepDao.class, () -> emptyMapper(EtlStepDao.class));
        context.registerBean(EtlStepItemDao.class, () -> emptyMapper(EtlStepItemDao.class));
        context.registerBean(EtlModuleDao.class, () -> emptyMapper(EtlModuleDao.class));
        context.registerBean(EtlDstTableDao.class, () -> emptyMapper(EtlDstTableDao.class));
        context.registerBean(WorkspaceMapper.class, () -> emptyMapper(WorkspaceMapper.class));
        context.registerBean(SysFolderMapper.class, () -> emptyMapper(SysFolderMapper.class));
        context.registerBean(EtlInstanceDao.class, () -> emptyMapper(EtlInstanceDao.class));
        context.registerBean(EtlGroupAdapterDao.class, () -> emptyMapper(EtlGroupAdapterDao.class));
    }
    static EtlFlowMapper flowMapper(Map<Long,EtlFlow> flows, AtomicInteger reads, boolean unavailable) {
        return (EtlFlowMapper)Proxy.newProxyInstance(IdentityBridgeProof.class.getClassLoader(), new Class[]{EtlFlowMapper.class}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                if (method.getName().equals("toString")) return "ReadOnlyMapperProof";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
            }
            if (!method.getName().equals("selectByPrimaryKey") || args.length != 1 || !(args[0] instanceof Long))
                throw new AssertionError("Only original mapper primary-key reads are allowed");
            reads.incrementAndGet();
            if (unavailable) throw new IllegalStateException("synthetic private database diagnostic must never be returned");
            return flows.get(args[0]);
        });
    }
    static Request scopeRequest(String body, int seed) { return new Request(body.getBytes(StandardCharsets.UTF_8), nonce(seed), IdentityBridgeFilter.AUTHORIZE_PATH); }
    static Response exchange(IdentityBridgeFilter filter, Request request) throws Exception {
        Response response = new Response();
        filter.doFilter(request.proxy(), response.proxy(), (a,b) -> { throw new AssertionError("signed operation entered original task chain"); });
        return response;
    }
    static void flowScopeAndFilter(Path root) throws Exception {
        AtomicInteger reads = new AtomicInteger();
        Map<Long,EtlFlow> flows = new HashMap<>();
        for (long id = 1; id <= 100; id++) flows.put(id, flow(id, 901));
        flows.put(101L, flow(101, 902)); flows.put(9_007_199_254_740_993L, flow(9_007_199_254_740_993L, 901));
        TicketVerifier tickets = verifier(child(root, "scope-replays"));
        IdentityBridgeFilter filter = filter(tickets, flowMapper(flows, reads, false));
        String allowed = "{\"subject\":\"rynew:user:7\",\"flowIds\":[\"1\",\"9007199254740993\"]}";
        Request good = scopeRequest(allowed, 80); Response result = exchange(filter, good);
        check(result.status == 200 && result.text.toString().equals("{\"code\":\"0\",\"data\":{\"authorized\":true}}"), "all mapped native flow scopes are accepted without returning objects");
        check(reads.get() == 2 && good.session == null, "authorization only reads original mapper and creates no session");
        check(exchange(filter, good).status == 401 && reads.get() == 2, "authorization replay is denied before another mapper read");
        Request sharedNonce = new Request(BODY, nonce(80));
        check(exchange(filter, sharedNonce).status == 401 && sharedNonce.session == null, "session and authorize share durable nonce consumption");
        String deniedBody = null;
        for (String ids : List.of("[\"101\"]", "[\"102\"]", "[\"1\",\"101\"]")) {
            Request request = scopeRequest("{\"subject\":\"rynew:user:7\",\"flowIds\":" + ids + "}", 81 + reads.get());
            Response response = exchange(filter, request);
            check(response.status == 403 && request.session == null, "unknown or outside-workspace flow fails closed");
            if (deniedBody == null) deniedBody = response.text.toString();
            else check(deniedBody.equals(response.text.toString()), "scope denial does not reveal object existence");
        }
        int beforeInvalid = reads.get(), seed = 100;
        for (String body : List.of(
            "{\"subject\":\"rynew:user:8\",\"flowIds\":[\"1\"]}",
            "{\"subject\":\"rynew:user:7\",\"flowIds\":[1]}",
            "{\"subject\":\"rynew:user:7\",\"flowIds\":[\"0\"]}",
            "{\"subject\":\"rynew:user:7\",\"flowIds\":[\"01\"]}",
            "{\"subject\":\"rynew:user:7\",\"flowIds\":[\"9223372036854775808\"]}",
            "{\"subject\":\"rynew:user:7\",\"flowIds\":[\"1\",\"1\"]}",
            "{\"subject\":\"rynew:user:7\",\"flowIds\":[]}",
            "{\"subject\":\"rynew:user:7\",\"flowIds\":[\"1\"],\"workspaceId\":902}",
            "{\"subject\":\"rynew:user:7\",\"flowIds\":[\"1\"],\"roleType\":1}",
            "{\"subject\":\"rynew:user:7\",\"flowIds\":[\"1\"],\"subject\":\"rynew:user:8\"}")) {
            check(exchange(filter, scopeRequest(body, seed++)).status == 401, "invalid authorization request rejected");
        }
        check(reads.get() == beforeInvalid, "invalid or unknown subjects never touch the mapper");
        List<String> ids = new ArrayList<>(); for (int id = 1; id <= 101; id++) ids.add(Integer.toString(id));
        check(exchange(filter, scopeRequest(IdentityMappings.JSON.writeValueAsString(Map.of("subject", "rynew:user:7", "flowIds", ids)), 120)).status == 401, "more than 100 IDs rejected");
        ids.remove(ids.size() - 1);
        check(exchange(filter, scopeRequest(IdentityMappings.JSON.writeValueAsString(Map.of("subject", "rynew:user:7", "flowIds", ids)), 121)).status == 200, "100 distinct original flow reads supported");
        Request wrongPath = new Request(allowed.getBytes(StandardCharsets.UTF_8), nonce(122)); wrongPath.servletPath = IdentityBridgeFilter.AUTHORIZE_PATH;
        int beforePath = reads.get(); check(exchange(filter, wrongPath).status == 401 && reads.get() == beforePath, "session-path signature cannot authorize objects");
        Request remote = scopeRequest(allowed, 123); remote.address = "192.0.2.20";
        check(exchange(filter, remote).status == 401 && reads.get() == beforePath, "authorization also requires loopback");
        IdentityBridgeFilter broken = filter(tickets, flowMapper(flows, reads, true));
        Request databaseFailure = scopeRequest(allowed, 124); Response failed = exchange(broken, databaseFailure);
        check(failed.status == 503 && !failed.text.toString().contains("diagnostic") && databaseFailure.session == null, "mapper unavailable never grants scope or leaks diagnostics");
        check(exchange(filter, databaseFailure).status == 401, "failed database read still consumes its one-time ticket");
    }
    static void defaultDisabled() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(IdentityBridgeAutoConfiguration.class); context.refresh();
            check(context.getBeansOfType(FilterRegistrationBean.class).isEmpty(), "missing enable flag installs no endpoint");
        }
        check(!new IdentityBridgeProperties().isEnabled(), "properties default disabled");
    }
    static void enabledConfiguration(Path root) throws Exception {
        Path key = root.resolve("configured-key"), mapping = root.resolve("configured-users.json");
        Files.write(key, KEY); Files.write(mapping, mapping());
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-------")); Files.setPosixFilePermissions(mapping, PosixFilePermissions.fromString("rw-------"));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(EtlFlowMapper.class, () -> flowMapper(Map.of(), new AtomicInteger(), false));
            registerOtherMappers(context);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("synthetic-bridge", Map.of(
                "rynew.detl.identity-bridge.enabled", "true", "rynew.detl.identity-bridge.secret-file", key.toString(),
                "rynew.detl.identity-bridge.mapping-file", mapping.toString(), "rynew.detl.identity-bridge.replay-directory", root.resolve("configured-replays").toString())));
            context.register(IdentityBridgeAutoConfiguration.class); context.refresh();
            Map<String,FilterRegistrationBean> beans = context.getBeansOfType(FilterRegistrationBean.class);
            check(beans.size() == 1, "explicit enable creates exactly one exchange filter");
            FilterRegistrationBean registration = beans.values().iterator().next();
            check(registration.getUrlPatterns().equals(Set.of("/_platform/session", "/_platform/authorize", "/_platform/authorize-save")), "filter registration is exact, never all paths");
            check(registration.getFilter() instanceof IdentityBridgeFilter, "enabled factory uses checked bridge filter");
        }
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(EtlFlowMapper.class, () -> flowMapper(Map.of(), new AtomicInteger(), false));
            registerOtherMappers(context);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("invalid-bridge", Map.of("rynew.detl.identity-bridge.enabled", "true")));
            context.register(IdentityBridgeAutoConfiguration.class);
            boolean rejected = false;
            try { context.refresh(); } catch (org.springframework.beans.BeansException expected) { rejected = true; }
            check(rejected, "enabled bridge with missing private configuration fails closed");
        }
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("no-original-mapper", Map.of(
                "rynew.detl.identity-bridge.enabled", "true", "rynew.detl.identity-bridge.secret-file", key.toString(),
                "rynew.detl.identity-bridge.mapping-file", mapping.toString(), "rynew.detl.identity-bridge.replay-directory", root.resolve("no-mapper-replays").toString())));
            context.register(IdentityBridgeAutoConfiguration.class);
            boolean rejected = false;
            try { context.refresh(); } catch (org.springframework.beans.BeansException expected) { rejected = true; }
            check(rejected, "enabled bridge without original mapper has no success fallback");
        }
    }
    static Object value(Class<?> type) { if (!type.isPrimitive()) return null; if (type == boolean.class) return false; if (type == long.class) return 0L; if (type == int.class) return 0; return null; }
    static class Session implements InvocationHandler {
        final Map<String,Object> attrs = new HashMap<>(); boolean invalid; int seconds;
        HttpSession proxy() { return (HttpSession)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpSession.class}, this); }
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch(method.getName()) { case "getAttribute": return attrs.get(args[0]); case "setAttribute": attrs.put((String)args[0], args[1]); return null; case "removeAttribute": attrs.remove(args[0]); return null; case "getAttributeNames": return Collections.enumeration(attrs.keySet()); case "invalidate": invalid = true; attrs.clear(); return null; case "setMaxInactiveInterval": seconds = (int)args[0]; return null; case "getMaxInactiveInterval": return seconds; case "getId": return "synthetic-session"; default: return value(method.getReturnType()); }
        }
    }
    static class Request implements InvocationHandler {
        byte[] body; String address = "127.0.0.1", method = "POST", servletPath = "/_platform/session", query; Session session;
        final Map<String,List<String>> headers = new HashMap<>(); final Map<String,Object> attributes = new HashMap<>();
        Request(byte[] body, String nonce) { this(body, nonce, IdentityBridgeFilter.SERVLET_PATH); }
        Request(byte[] body, String nonce, String path) { this.body = body; servletPath = path; headers.put("X-RYNEW-Timestamp", List.of(Long.toString(NOW))); headers.put("X-RYNEW-Nonce", List.of(nonce)); headers.put("X-RYNEW-Signature", List.of(signature("POST", "/detl" + path, NOW, nonce, body))); }
        HttpServletRequest proxy() { return (HttpServletRequest)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpServletRequest.class}, this); }
        public Object invoke(Object proxy, Method m, Object[] args) {
            switch(m.getName()) {
                case "getMethod": return method; case "getServletPath": return servletPath; case "getContextPath": return "/detl"; case "getRequestURI": return "/detl" + servletPath; case "getRemoteAddr": return address; case "getQueryString": return query; case "getContentType": return "application/json;charset=UTF-8"; case "getContentLengthLong": return (long)body.length;
                case "getHeader": return headers.containsKey(args[0]) ? headers.get(args[0]).get(0) : null; case "getHeaders": return Collections.enumeration(headers.getOrDefault(args[0], List.of()));
                case "getAttribute": return attributes.get(args[0]); case "setAttribute": attributes.put((String)args[0], args[1]); return null; case "removeAttribute": attributes.remove(args[0]); return null; case "getAttributeNames": return Collections.enumeration(attributes.keySet());
                case "getSession": if (session != null && session.invalid) session = null; if (session == null && (args == null || args.length == 0 || Boolean.TRUE.equals(args[0]))) session = new Session(); return session == null ? null : session.proxy();
                case "getInputStream": ByteArrayInputStream input = new ByteArrayInputStream(body); return new ServletInputStream() { public int read() { return input.read(); } public boolean isFinished() { return input.available() == 0; } public boolean isReady() { return true; } public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); } };
                default: return value(m.getReturnType());
            }
        }
    }
    static class Response implements InvocationHandler {
        int status = 200; final StringWriter text = new StringWriter(); final Map<String,String> headers = new HashMap<>();
        HttpServletResponse proxy() { return (HttpServletResponse)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpServletResponse.class}, this); }
        public Object invoke(Object proxy, Method method, Object[] args) { switch(method.getName()) { case "setStatus": status = (int)args[0]; return null; case "getStatus": return status; case "setHeader": headers.put((String)args[0], (String)args[1]); return null; case "getWriter": return new PrintWriter(text); default: return value(method.getReturnType()); } }
    }
}
