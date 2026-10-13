package com.rynew.detl.bridge;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Exact-wire HMAC verification and durable one-time consumption. Contains no identity defaults. */
final class TicketVerifier {
    static final long MAX_AGE_MILLIS = 60_000, FUTURE_SKEW_MILLIS = 5_000;
    static final int MAX_BODY_BYTES = 4096, MAX_NONCES = 8192;
    private final byte[] key;
    private final Path replayRoot;
    private final Clock clock;
    TicketVerifier(byte[] key, Path replayRoot, Clock clock) {
        if (key.length < 32 || key.length > 64) throw new IllegalArgumentException("Signing key must be 32 to 64 raw bytes");
        this.key = key.clone(); this.replayRoot = replayRoot; this.clock = clock;
    }
    long verify(String method, String uri, byte[] body, String timestamp, String nonce, String signature) {
        return verify(method, uri, body, timestamp, nonce, signature, MAX_BODY_BYTES);
    }
    long verify(String method, String uri, byte[] body, String timestamp, String nonce, String signature, int maximum) {
        try {
            if (maximum < 1 || maximum > NativeSaveAuthorizer.MAX_ENVELOPE_BYTES || !"POST".equals(method) || uri == null || uri.indexOf('\n') >= 0 || uri.indexOf('\r') >= 0 || body.length > maximum
                || timestamp == null || !timestamp.matches("[1-9][0-9]{9,16}") || nonce == null || !nonce.matches("[A-Za-z0-9_-]{43}")
                || signature == null || !signature.matches("[0-9a-f]{64}")) throw denied();
            byte[] decoded = Base64.getUrlDecoder().decode(nonce);
            if (decoded.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(nonce)) throw denied();
            long issuedAt = Long.parseLong(timestamp), now = clock.millis(), expires = Math.addExact(issuedAt, MAX_AGE_MILLIS);
            if (issuedAt > now + FUTURE_SKEW_MILLIS || now > expires) throw denied();
            if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.US_ASCII), sign(key, method, uri, timestamp, nonce, body).getBytes(StandardCharsets.US_ASCII))) throw denied();
            return expires;
        } catch (Exception ignored) { throw denied(); }
    }
    synchronized void consume(String nonce, long expires) throws IOException {
        if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{43}") || expires < clock.millis()) throw denied();
        PrivateFiles.replayDirectory(replayRoot.toString());
        int count = 0;
        try (var entries = Files.newDirectoryStream(replayRoot)) {
            for (Path file : entries) {
                String name = file.getFileName().toString();
                if (name.equals(PrivateFiles.REPLAY_MARKER)) continue;
                if (!name.matches("[A-Za-z0-9_-]{43}\\.nonce") || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Replay storage is unavailable");
                PrivateFiles.requirePrivate(file); count++;
                if (Files.size(file) > 32) continue;
                try {
                    long expiry = Long.parseLong(Files.readString(file, StandardCharsets.US_ASCII));
                    if (expiry < clock.millis()) { Files.delete(file); count--; }
                } catch (NumberFormatException ignored) { /* A partial crash record is consumed, never reusable. */ }
            }
        }
        if (count >= MAX_NONCES) throw new IOException("Replay storage capacity reached");
        Path file = replayRoot.resolve(nonce + ".nonce");
        try {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try (var channel = FileChannel.open(file, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer bytes = StandardCharsets.US_ASCII.encode(Long.toString(expires));
                while (bytes.hasRemaining()) channel.write(bytes); channel.force(true);
            }
            try (var directory = FileChannel.open(replayRoot, StandardOpenOption.READ)) { directory.force(true); }
        } catch (FileAlreadyExistsException replay) { throw denied(); }
    }
    static String sign(byte[] key, String method, String uri, String timestamp, String nonce, byte[] body) {
        try {
            String canonical = method + "\n" + uri + "\n" + timestamp + "\n" + nonce + "\n" + hex(MessageDigest.getInstance("SHA-256").digest(body));
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) { throw new IllegalStateException("Signing unavailable"); }
    }
    static String hex(byte[] value) { char[] digits = "0123456789abcdef".toCharArray(), result = new char[value.length * 2]; for (int i = 0; i < value.length; i++) { result[i * 2] = digits[(value[i] >>> 4) & 15]; result[i * 2 + 1] = digits[value[i] & 15]; } return new String(result); }
    static SecurityException denied() { return new SecurityException("Identity exchange denied"); }
}
