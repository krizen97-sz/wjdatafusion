package com.hm.manage.service.governance.hdi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HdiCoreKeysTest {
    @TempDir Path directory;
    @Test void originalFormRsaProtocolUsesPersistentPrivateKeyAndRoundTripsWithoutExposingIt() throws Exception {
        directory = directory.toRealPath(); Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        var properties = new HdiCoreProperties(); properties.setStateDir(directory.toString()); var keys = new HdiCoreKeys(properties);
        String pem = keys.publicKey(); assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----")); assertFalse(pem.contains("PRIVATE"));
        var publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""))));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding"); cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        String encrypted = Base64.getEncoder().encodeToString(cipher.doFinal("本次测试内容".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("本次测试内容", keys.decrypt(encrypted)); assertEquals(pem, new HdiCoreKeys(properties).publicKey());
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(directory.resolve("keys/form-rsa.json")));
        assertThrows(HdiTaskFault.class, () -> keys.decrypt("not a ciphertext"));
    }
    @Test void unsafePrivateFilePermissionsAndSymlinksAreRefused() throws Exception {
        directory = directory.toRealPath(); Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        var properties = new HdiCoreProperties(); properties.setStateDir(directory.toString()); new HdiCoreKeys(properties).publicKey();
        var file = directory.resolve("keys/form-rsa.json"); Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        assertEquals(503, assertThrows(HdiTaskFault.class, () -> new HdiCoreKeys(properties).publicKey()).status());
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------")); var moved = directory.resolve("outside.json"); Files.move(file, moved); Files.createSymbolicLink(file, moved);
        assertThrows(HdiTaskFault.class, () -> new HdiCoreKeys(properties).publicKey());
    }
}
