package com.hm.manage.service.governance.hdi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import org.springframework.stereotype.Component;

/** Real platform RSA key pair for the original form encryption protocol. No browser decryption endpoint. */
@Component
public class HdiCoreKeys {
    private final HdiCoreProperties properties;
    private KeyPair pair;
    public HdiCoreKeys(HdiCoreProperties properties) { this.properties = properties; }
    public String publicKey() {
        byte[] encoded = keyPair().getPublic().getEncoded();
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded) + "\n-----END PUBLIC KEY-----";
    }
    public String decrypt(String ciphertext) {
        try {
            if (ciphertext == null || ciphertext.length() > 512) throw new IllegalArgumentException();
            byte[] encrypted = Base64.getDecoder().decode(ciphertext);
            if (encrypted.length != 256) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding"); cipher.init(Cipher.DECRYPT_MODE, keyPair().getPrivate());
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(cipher.doFinal(encrypted))).toString();
        } catch (HdiTaskFault error) { throw error; }
        catch (Exception error) { throw new HdiTaskFault(400, "节点密文格式无效"); }
    }
    private synchronized KeyPair keyPair() {
        if (pair != null) return pair;
        Path temporary = null;
        try {
            Path state = Path.of(properties.getStateDir());
            if (!state.isAbsolute()) throw new IOException("Private state directory is required");
            for (Path parent = state; parent != null; parent = parent.getParent()) {
                if (Files.isSymbolicLink(parent) || Files.exists(parent.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe private state path");
            }
            if (!Files.isDirectory(state, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Private state directory is not initialized");
            checkPrivate(state, true);
            Path directory = state.resolve("keys");
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            checkPrivate(directory, true);
            Path file = directory.resolve("form-rsa.json");
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                checkPrivate(file, false); if (Files.size(file) > 8192) throw new IOException("Key file is invalid");
                var data = HdiProtocol.JSON.readTree(Files.readAllBytes(file)); KeyFactory factory = KeyFactory.getInstance("RSA");
                pair = new KeyPair(factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(data.path("publicKey").asText()))),
                    factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(data.path("privateKey").asText()))));
            } else {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); KeyPair created = generator.generateKeyPair();
                var data = HdiProtocol.JSON.createObjectNode().put("publicKey", Base64.getEncoder().encodeToString(created.getPublic().getEncoded()))
                    .put("privateKey", Base64.getEncoder().encodeToString(created.getPrivate().getEncoded()));
                temporary = Files.createTempFile(directory, "rsa-", ".part", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                Files.write(temporary, HdiProtocol.JSON.writeValueAsBytes(data));
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE); temporary = null; pair = created;
            }
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding"); cipher.init(Cipher.ENCRYPT_MODE, pair.getPublic());
            byte[] probe = "platform-key-pair-check".getBytes(StandardCharsets.UTF_8), encrypted = cipher.doFinal(probe);
            cipher.init(Cipher.DECRYPT_MODE, pair.getPrivate());
            if (!java.security.MessageDigest.isEqual(probe, cipher.doFinal(encrypted))) { pair = null; throw new IOException("Mismatched key pair"); }
            return pair;
        } catch (Exception error) { pair = null; throw new HdiTaskFault(503, "平台节点加密密钥不可用，请检查私有运行目录"); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }
    private static void checkPrivate(Path path, boolean directory) throws IOException {
        if (Files.isSymbolicLink(path) || (directory ? !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) : !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))) throw new IOException("Invalid private key path");
        var permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
        if (permissions.stream().anyMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_"))) throw new IOException("Private key permissions invalid");
        if (!Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).getName().equals(System.getProperty("user.name"))) throw new IOException("Private key owner mismatch");
    }
}
