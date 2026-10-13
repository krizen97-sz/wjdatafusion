package com.hm.manage.service.governance;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DataGovernanceHdiClientTest
{
    @TempDir Path temporary;
    private HttpServer server;
    private DataGovernanceHdiProperties properties;
    private Path key;
    private final String success = "{\"code\":\"0\",\"data\":{\"user\":{\"id\":7,\"accountName\":\"mapped-user\"},"
        + "\"projectCode\":\"local-project\",\"projectName\":\"本地项目\",\"projectList\":[],\"unexpected\":\"not copied\"}}";

    @BeforeEach void setup() throws IOException
    {
        key = temporary.toRealPath().resolve("identity.key");
        byte[] bytes = new byte[32]; for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        Files.write(key, bytes); Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-------"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.start();
        properties = new DataGovernanceHdiProperties(); properties.setEnabled(true);
        properties.setTaskServiceUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/detl");
        properties.setIdentityKeyFile(key.toString());
    }

    @AfterEach void cleanup() { if (server != null) server.stop(0); }

    @Test void signingMatchesIndependentPythonHmacVector() throws Exception
    {
        String result = DataGovernanceHdiClient.sign(Files.readAllBytes(key), "POST", DataGovernanceHdiClient.EXCHANGE_PATH,
            "1791861413220", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "{\"subject\":\"rynew:user:1\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("260f22fd47ad081388b7360013a33bad757b2a2df4005e6341ded29c55dc34c2", result);
        assertNotEquals(result, DataGovernanceHdiClient.sign(Files.readAllBytes(key), "GET", DataGovernanceHdiClient.EXCHANGE_PATH,
            "1791861413220", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "{}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test void rejectsRemoteRedirectedAmbiguousOrPrivilegedTargetsBeforeSendingIdentity() throws Exception
    {
        for (String url : new String[] { "https://127.0.0.1:19312/detl", "http://localhost:19312/detl",
            "http://192.168.16.10:19312/detl", "http://127.0.0.1:80/detl", "http://user@127.0.0.1:19312/detl",
            "http://127.0.0.1:19312/detl?target=remote", "http://127.0.0.1:19312/detl#x",
            "http://127.0.0.1:19312/detl/../login", "http://127.0.0.1:19312/%64etl" })
            assertThrows(IOException.class, () -> DataGovernanceHdiClient.localServiceUri(url), url);
    }

    @Test void disabledModuleAndInvalidSubjectNeverContactTheNativeService()
    {
        properties.setEnabled(false);
        assertThrows(IOException.class, () -> new DataGovernanceHdiClient(properties).exchange(1));
        properties.setEnabled(true);
        assertThrows(IOException.class, () -> new DataGovernanceHdiClient(properties).exchange(0));
    }

    @Test void rejectsSharedShortAndSymbolicKeys() throws Exception
    {
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(IOException.class, () -> DataGovernanceHdiClient.privateKey(key));
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-------"));
        Path link = key.resolveSibling("linked.key"); Files.createSymbolicLink(link, key);
        assertThrows(IOException.class, () -> DataGovernanceHdiClient.privateKey(link));
        Files.writeString(key, "short");
        assertThrows(IOException.class, () -> DataGovernanceHdiClient.privateKey(key));
    }

    @Test void usesOriginalEnvelopeKeepsCookiesPrivateAndDoesNotSendBrowserPrivileges() throws Exception
    {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> nonce = new AtomicReference<>();
        server.createContext(DataGovernanceHdiClient.EXCHANGE_PATH, exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            nonce.set(exchange.getRequestHeaders().getFirst("X-RYNEW-Nonce"));
            exchange.getResponseHeaders().add("Set-Cookie", "SESSION=private-original-session; HttpOnly; Path=/detl");
            byte[] response = success.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        var session = new DataGovernanceHdiClient(properties).exchange(12);
        assertEquals("{\"subject\":\"rynew:user:12\"}", body.get());
        assertTrue(nonce.get().matches("[A-Za-z0-9_-]{43}"));
        assertEquals("SESSION=private-original-session", session.cookieHeader());
        assertEquals("local-project", session.context().get("projectCode"));
        assertFalse(session.context().containsKey("unexpected"));
        assertFalse(session.context().toString().contains("private-original-session"));
        assertFalse(session.toString().contains("private-original-session"));
    }

    @Test void neverFollowsLoginRedirectOrLeaksNativeFailureBody() throws Exception
    {
        AtomicInteger redirected = new AtomicInteger();
        server.createContext("/login", exchange -> { redirected.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.createContext(DataGovernanceHdiClient.EXCHANGE_PATH, exchange -> {
            exchange.getResponseHeaders().add("Location", "/login");
            byte[] body = "native-secret-must-not-leak".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(302, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        IOException error = assertThrows(IOException.class, () -> new DataGovernanceHdiClient(properties).exchange(1));
        assertEquals(0, redirected.get()); assertFalse(error.toString().contains("native-secret"));
    }

    @Test void refusesSuccessWithoutOriginalCookieAndDoesNotAcceptPlatformCode200() throws Exception
    {
        AtomicReference<String> response = new AtomicReference<>(success);
        server.createContext(DataGovernanceHdiClient.EXCHANGE_PATH, exchange -> {
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        assertThrows(IOException.class, () -> new DataGovernanceHdiClient(properties).exchange(1));
        response.set(success.replace("\"0\"", "200"));
        assertThrows(IOException.class, () -> new DataGovernanceHdiClient(properties).exchange(1));
    }

    @Test void malformedOrOversizedNativeResponseIsNotReflectedToUser() throws Exception
    {
        AtomicReference<String> response = new AtomicReference<>("{secret: native-secret-must-not-leak}");
        server.createContext(DataGovernanceHdiClient.EXCHANGE_PATH, exchange -> {
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        IOException error = assertThrows(IOException.class, () -> new DataGovernanceHdiClient(properties).exchange(1));
        assertFalse(error.toString().contains("native-secret"));
        response.set("x".repeat(256 * 1024 + 1));
        assertThrows(IOException.class, () -> new DataGovernanceHdiClient(properties).exchange(1));
    }
    @Test void authorizesOnlyServerDerivedSubjectAndStringIdsWithFullPathSignature() throws Exception {
        AtomicReference<String> payload = new AtomicReference<>(); AtomicReference<Boolean> signatureOk = new AtomicReference<>(false);
        server.createContext(DataGovernanceHdiClient.AUTHORIZE_PATH, exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes(); payload.set(new String(body, StandardCharsets.UTF_8));
            try {
                String expected = DataGovernanceHdiClient.sign(Files.readAllBytes(key), "POST", DataGovernanceHdiClient.AUTHORIZE_PATH,
                    exchange.getRequestHeaders().getFirst("X-RYNEW-Timestamp"), exchange.getRequestHeaders().getFirst("X-RYNEW-Nonce"), body);
                signatureOk.set(expected.equals(exchange.getRequestHeaders().getFirst("X-RYNEW-Signature")));
            } catch (Exception error) { throw new IOException(error); }
            assertNull(exchange.getRequestHeaders().getFirst("Cookie")); assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"code\":\"0\",\"data\":{\"authorized\":true}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        new DataGovernanceHdiClient(properties).authorize(7, List.of("9007199254740993", "9223372036854775807"));
        assertTrue(signatureOk.get());
        assertEquals("{\"subject\":\"rynew:user:7\",\"flowIds\":[\"9007199254740993\",\"9223372036854775807\"]}", payload.get());
    }

    @Test void invalidAuthorizationIdsNeverContactNativeService() {
        var client = new DataGovernanceHdiClient(properties);
        for (List<String> ids : List.of(List.<String>of(), List.of("0"), List.of("-1"), List.of("1.0"), List.of("01"), List.of("9223372036854775808"), java.util.Collections.nCopies(101, "1")))
            assertThrows(IOException.class, () -> client.authorize(7, ids));
        assertThrows(IOException.class, () -> client.authorize(0, List.of("1")));
        properties.setEnabled(false); assertThrows(IOException.class, () -> client.authorize(7, List.of("1")));
    }

    @Test void authorizationDenialFailureMalformedAndOversizedResponsesFailClosedWithoutNativeBody() throws Exception {
        AtomicInteger status = new AtomicInteger(403); AtomicReference<String> response = new AtomicReference<>("private-native-secret");
        server.createContext(DataGovernanceHdiClient.AUTHORIZE_PATH, exchange -> {
            exchange.getRequestBody().readAllBytes(); byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        var client = new DataGovernanceHdiClient(properties);
        assertThrows(DataGovernanceHdiClient.ScopeDenied.class, () -> client.authorize(7, List.of("99")));
        for (int code : List.of(401, 503, 500)) {
            status.set(code); var error = assertThrows(IOException.class, () -> client.authorize(7, List.of("1")));
            assertFalse(error.toString().contains("private-native-secret"));
        }
        status.set(200);
        for (String body : List.of("private-native-secret", "{\"code\":\"0\",\"data\":{\"authorized\":\"true\"}}", "{\"code\":200,\"data\":{\"authorized\":true}}", "x".repeat(8193))) {
            response.set(body); var error = assertThrows(IOException.class, () -> client.authorize(7, List.of("1")));
            assertFalse(error.toString().contains("private-native-secret"));
        }
    }

    @Test void authorizationNeverFollowsNativeRedirects() throws Exception {
        AtomicInteger redirected = new AtomicInteger();
        server.createContext("/unexpected", exchange -> { redirected.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.createContext(DataGovernanceHdiClient.AUTHORIZE_PATH, exchange -> {
            exchange.getRequestBody().readAllBytes(); exchange.getResponseHeaders().add("Location", "/unexpected");
            exchange.sendResponseHeaders(307, -1); exchange.close();
        });
        assertThrows(IOException.class, () -> new DataGovernanceHdiClient(properties).authorize(7, List.of("1")));
        assertEquals(0, redirected.get());
    }

}
