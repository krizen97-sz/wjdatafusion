package com.hm.manage.service.document;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.zip.UnicodePathExtraField;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.manage.config.DocumentManagementProperties;

class DocumentTransferZipCompatibilityTest
{
    // Real independently generated archives, not encrypted-flag-only stubs:
    // Info-ZIP 3.0 ZipCrypto and pyzipper 0.3.6 AES-256. Both were decrypted
    // successfully with their generating tools using password "fixture-test-only".
    private static final String ZIP_CRYPTO =
        "UEsDBAoACQAAAANRMl2evOk3OwAAAC8AAAAKABwAcmVhZG1lLnR4dFVUCQADBZ2sagWdrGp1eAsAAQT1AQAABAAAAACqqSIs27uz1qH7c5zEM//Hau4Pq+9dXd2qhN+AF10EWuXftR0xuyxLlKFTVPi6IjbEE26GjZhCY7mqO1BLBwievOk3OwAAAC8AAABQSwECHgMKAAkAAAADUTJdnrzpNzsAAAAvAAAACgAYAAAAAAABAAAApIEAAAAAcmVhZG1lLnR4dFVUBQADBZ2sanV4CwABBPUBAAAEAAAAAFBLBQYAAAAAAQABAFAAAACPAAAAAAA=";
    private static final String ZIP_AES =
        "UEsDBBQAAQBjAAJRMl2evOk3TQAAAC8AAAAKAAsAcmVhZG1lLnR4dAGZBwABAEFFAwgAwAoD9Qnt04BOi8AqF1OjdD3KTd4WOY2M/Q1ToEv2dv7WW1lHOYfL4wzeluPInZ7Z3tzYhizn3N/KQvZMfyo1teyABCkfaPb9HSnwsA9QSwECFAMUAAEAYwACUTJdnrzpN00AAAAvAAAACgALAAAAAAAAAAAAgAEAAAAAcmVhZG1lLnR4dAGZBwABAEFFAwgAUEsFBgAAAAABAAEAQwAAAIAAAAAAAA==";

    @TempDir
    Path temporary;
    private DocumentStorageService service;
    private DocumentManagementProperties properties;

    @BeforeEach
    void setUp()
    {
        properties = new DocumentManagementProperties();
        properties.setStorageRoot(temporary.toString());
        service = new DocumentStorageService();
        ReflectionTestUtils.setField(service, "properties", properties);
    }

    @Test
    void shouldAcceptWindowsGbkNamesThatPreviouslyFailedTheUtf8OnlyParser() throws Exception
    {
        Path archive = writeArchive("gbk.zip", "GBK", false, Zip64Mode.AsNeeded, "目录/会议资料.txt");

        assertThrows(ZipException.class, () -> new ZipFile(archive.toFile()));
        assertAcceptedUnchanged(archive);
    }

    @Test
    void shouldAcceptUtf8UnicodeExtraFieldsAndLegacySingleByteNames() throws Exception
    {
        assertAcceptedUnchanged(writeArchive("utf8.zip", "UTF-8", false, Zip64Mode.AsNeeded, "目录/资料.txt"));
        assertAcceptedUnchanged(writeArchive("unicode-extra.zip", "GBK", true, Zip64Mode.AsNeeded, "目录/资料.txt"));
        assertAcceptedUnchanged(writeArchive("cp437.zip", "Cp437", false, Zip64Mode.AsNeeded, "café.txt"));
    }

    @Test
    void shouldAcceptActualZipCryptoAndAesEncryptedMembersWithoutDecrypting() throws Exception
    {
        for (String fixture : new String[] { ZIP_CRYPTO, ZIP_AES })
        {
            Path archive = Files.write(temporary.resolve("encrypted-" + fixture.length() + ".zip"),
                Base64.getDecoder().decode(fixture));
            assertThrows(ZipException.class, () -> new ZipFile(archive.toFile()));
            assertAcceptedUnchanged(archive);
        }
    }

    @Test
    void shouldAcceptZip64AndAnEmptyZip() throws Exception
    {
        assertAcceptedUnchanged(writeArchive("zip64.zip", "UTF-8", false, Zip64Mode.Always, "资料.txt"));
        Path empty = temporary.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(empty)))
        {
            output.setComment("合法的空压缩包");
        }
        assertAcceptedUnchanged(empty);
    }

    @Test
    void shouldStillRejectGbkTraversalAndUnsafeUnicodeAliases() throws Exception
    {
        for (String path : new String[] { "../资料.txt", "目录/../../资料.txt", "..\\资料.txt", "/资料.txt" })
        {
            Path archive = writeArchive("unsafe.zip", "GBK", false, Zip64Mode.AsNeeded, path);
            ServiceException error = assertThrows(ServiceException.class,
                () -> service.validateUploadedArchiveFile(archive, "zip"));
            assertTrue(error.getMessage().contains("路径"));
        }
        Path alias = temporary.resolve("alias.zip");
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(alias.toFile()))
        {
            output.setEncoding("GBK");
            ZipArchiveEntry entry = new ZipArchiveEntry("../secret.txt");
            entry.addExtraField(new UnicodePathExtraField("safe-name.txt",
                "../secret.txt".getBytes(StandardCharsets.US_ASCII)));
            output.putArchiveEntry(entry);
            output.write("data".getBytes(StandardCharsets.US_ASCII));
            output.closeArchiveEntry();
        }
        assertThrows(ServiceException.class, () -> service.validateUploadedArchiveFile(alias, "zip"));
    }

    @Test
    void shouldRejectDuplicatesAndEnforceTheEntryLimitBeforeParsingAllMembers() throws Exception
    {
        Path duplicate = writeArchive("duplicate.zip", "GBK", false, Zip64Mode.AsNeeded, "资料.txt", "资料.txt");
        assertTrue(assertThrows(ServiceException.class,
            () -> service.validateUploadedArchiveFile(duplicate, "zip")).getMessage().contains("重复条目"));
        properties.setMaxArchiveEntries(100);
        String[] names = new String[101];
        Arrays.setAll(names, index -> "资料/" + index + ".txt");
        Path many = writeArchive("many.zip", "GBK", false, Zip64Mode.AsNeeded, names);
        assertTrue(assertThrows(ServiceException.class,
            () -> service.validateUploadedArchiveFile(many, "zip")).getMessage().contains("条目超过100个"));
    }

    @Test
    void shouldRejectRawUnicodeAndCrossAliasCollisionsAcrossMembers() throws Exception
    {
        for (String[][] entries : new String[][][] {
            { { "duplicate.txt", "alias-0.txt" }, { "duplicate.txt", "alias-1.txt" } },
            { { "raw-0.txt", "alias.txt" }, { "raw-1.txt", "alias.txt" } },
            { { "raw-0.txt", "raw-1.txt" }, { "raw-1.txt", "alias-1.txt" } },
            { { "raw-0.txt", "alias-0.txt" }, { "raw-1.txt", "raw-0.txt" } }
        })
        {
            Path archive = writeAliasedArchive(entries);
            assertTrue(assertThrows(ServiceException.class,
                () -> service.validateUploadedArchiveFile(archive, "zip")).getMessage().contains("重复条目"));
        }
        assertAcceptedUnchanged(writeAliasedArchive(new String[][] { { "same.txt", "same.txt" } }));
    }

    @Test
    void shouldValidateBothLocalAndCentralUnicodeAliasesIncludingStaleLocalExtraFields() throws Exception
    {
        byte[] original = Files.readAllBytes(writeAliasedArchive(new String[][] { { "raw.txt", "alias-0.txt" } }));
        int localExtra = unicodeExtraOffset(original, false);
        int centralExtra = unicodeExtraOffset(original, true);
        byte[] unsafeName = "../hack.txt".getBytes(StandardCharsets.UTF_8);
        for (int extra : new int[] { localExtra, centralExtra })
        {
            byte[] unsafe = original.clone();
            System.arraycopy(unsafeName, 0, unsafe, extra + 9, unsafeName.length);
            assertRejected(unsafe);
        }
        // A local extra field with a stale raw-name checksum must not shadow a
        // valid, unsafe central-directory Unicode name.
        byte[] staleLocal = original.clone();
        staleLocal[localExtra + 5] ^= 1;
        System.arraycopy(unsafeName, 0, staleLocal, centralExtra + 9, unsafeName.length);
        assertRejected(staleLocal);
        byte[] malformedUtf8 = original.clone();
        malformedUtf8[centralExtra + 9] = (byte) 0xff;
        assertRejected(malformedUtf8);

        // Per the ZIP specification, a Unicode field with an invalid checksum
        // is ignored. The underlying raw name remains safe and unique.
        byte[] ignoredAliases = original.clone();
        for (int extra : new int[] { localExtra, centralExtra })
        {
            ignoredAliases[extra + 5] ^= 1;
            System.arraycopy(unsafeName, 0, ignoredAliases, extra + 9, unsafeName.length);
        }
        assertAcceptedUnchanged(Files.write(temporary.resolve("ignored-aliases.zip"), ignoredAliases));
    }

    @Test
    void shouldRejectContradictoryZipAndZip64DirectoryFields() throws Exception
    {
        byte[] original = Files.readAllBytes(writeArchive("zip64-directory.zip", "UTF-8", false,
            Zip64Mode.Always, "safe.txt"));
        int end = findSignature(original, 0x06054b50);
        int zip64 = findSignature(original, 0x06064b50);
        for (int[] offsets : new int[][] { { 12, 40 }, { 16, 48 } })
        {
            byte[] contradiction = original.clone();
            ByteBuffer data = ByteBuffer.wrap(contradiction).order(ByteOrder.LITTLE_ENDIAN);
            data.putInt(end + offsets[0], (int) data.getLong(zip64 + offsets[1]) + 1);
            assertRejected(contradiction);
        }
    }

    @Test
    void shouldRejectContradictoryLocalSizesAndCrcWithAndWithoutZip64() throws Exception
    {
        byte[] normal = Files.readAllBytes(writeArchive("local-sizes.zip", "UTF-8", false,
            Zip64Mode.AsNeeded, "safe.txt"));
        for (int offset : new int[] { 14, 18, 22 })
        {
            byte[] contradiction = normal.clone();
            ByteBuffer data = ByteBuffer.wrap(contradiction).order(ByteOrder.LITTLE_ENDIAN);
            data.putInt(offset, data.getInt(offset) + 1);
            assertRejected(contradiction);
        }
        byte[] zip64 = Files.readAllBytes(writeArchive("local-sizes-zip64.zip", "UTF-8", false,
            Zip64Mode.Always, "safe.txt"));
        int zip64Extra = 30 + Short.toUnsignedInt(ByteBuffer.wrap(zip64).order(ByteOrder.LITTLE_ENDIAN).getShort(26));
        for (int relative : new int[] { 4, 12 })
        {
            byte[] contradiction = zip64.clone();
            ByteBuffer data = ByteBuffer.wrap(contradiction).order(ByteOrder.LITTLE_ENDIAN);
            data.putLong(zip64Extra + relative, data.getLong(zip64Extra + relative) + 1);
            assertRejected(contradiction);
        }
        Path descriptor = temporary.resolve("descriptor.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(descriptor)))
        {
            output.putNextEntry(new java.util.zip.ZipEntry("safe.txt"));
            output.write("valid streamed member with zero local sizes".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        assertAcceptedUnchanged(descriptor);
    }

    @Test
    void shouldRejectTruncatedArchivesAndCorruptedCentralOrLocalMetadata() throws Exception
    {
        Path source = writeArchive("source.zip", "GBK", false, Zip64Mode.AsNeeded, "资料.txt");
        byte[] original = Files.readAllBytes(source);
        int central = findSignature(original, 0x02014b50);
        int end = findSignature(original, 0x06054b50);
        assertRejected(Arrays.copyOf(original, original.length - 1));
        assertRejected(Arrays.copyOf(original, original.length - 22));
        byte[] badCentralSignature = original.clone();
        badCentralSignature[central] = 0;
        assertRejected(badCentralSignature);
        byte[] badLocalSignature = original.clone();
        badLocalSignature[2] = 7;
        badLocalSignature[3] = 8;
        assertRejected(badLocalSignature);
        byte[] invalidOffset = original.clone();
        ByteBuffer.wrap(invalidOffset).order(ByteOrder.LITTLE_ENDIAN).putInt(central + 42, original.length + 1);
        assertRejected(invalidOffset);
        byte[] truncatedPayload = original.clone();
        ByteBuffer.wrap(truncatedPayload).order(ByteOrder.LITTLE_ENDIAN).putInt(central + 20, original.length);
        assertRejected(truncatedPayload);
        byte[] wrongCount = original.clone();
        ByteBuffer.wrap(wrongCount).order(ByteOrder.LITTLE_ENDIAN).putShort(end + 8, (short) 2).putShort(end + 10, (short) 2);
        assertRejected(wrongCount);
        byte[] differentLocalName = original.clone();
        differentLocalName[30] = 'x';
        assertRejected(differentLocalName);
    }

    @Test
    void shouldRejectAnIncompleteMultiVolumeArchiveAndTruncatedZip64Metadata() throws Exception
    {
        Path ordinary = writeArchive("single.zip", "UTF-8", false, Zip64Mode.AsNeeded, "safe.txt");
        byte[] split = Files.readAllBytes(ordinary);
        ByteBuffer.wrap(split).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(findSignature(split, 0x06054b50) + 4, (short) 1);
        assertRejected(split);
        Path zip64 = writeArchive("zip64-source.zip", "UTF-8", false, Zip64Mode.Always, "safe.txt");
        byte[] invalid = Files.readAllBytes(zip64);
        ByteBuffer.wrap(invalid).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(findSignature(invalid, 0x07064b50) + 8, Long.MAX_VALUE);
        assertRejected(invalid);
    }

    @Test
    void shouldNotReinterpretInvalidDeclaredUtf8AsGbk() throws Exception
    {
        Path source = writeArchive("declared-utf8.zip", "UTF-8", false, Zip64Mode.AsNeeded, "safe.txt");
        byte[] invalid = Files.readAllBytes(source);
        invalid[30] = (byte) 0xff;
        invalid[findSignature(invalid, 0x02014b50) + 46] = (byte) 0xff;
        assertRejected(invalid);
    }

    @Test
    void shouldNotLoosenOfficeValidationForTransferCompatibleZipFiles() throws Exception
    {
        Path gbk = writeArchive("disguised.docx", "GBK", false, Zip64Mode.AsNeeded, "资料.txt");
        Path encrypted = Files.write(temporary.resolve("encrypted.docx"), Base64.getDecoder().decode(ZIP_AES));

        assertThrows(ServiceException.class, () -> service.validateUploadedOfficeFile(gbk, "docx"));
        assertThrows(ServiceException.class, () -> service.validateUploadedOfficeFile(encrypted, "docx"));
    }

    private Path writeArchive(String filename, String encoding, boolean unicode, Zip64Mode zip64, String... names)
        throws Exception
    {
        Path archive = temporary.resolve(filename);
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(archive.toFile()))
        {
            output.setEncoding(encoding);
            output.setUseZip64(zip64);
            output.setCreateUnicodeExtraFields(unicode ? ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS
                : ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER);
            for (String name : names)
            {
                output.putArchiveEntry(new ZipArchiveEntry(name));
                output.write("transfer-only test content".getBytes(StandardCharsets.UTF_8));
                output.closeArchiveEntry();
            }
        }
        return archive;
    }

    private void assertAcceptedUnchanged(Path archive) throws Exception
    {
        byte[] original = Files.readAllBytes(archive);
        assertTrue(service.validateUploadedArchiveFile(archive, "zip").warnings().isEmpty());
        assertArrayEquals(original, Files.readAllBytes(archive));
    }

    private Path writeAliasedArchive(String[][] entries) throws Exception
    {
        Path archive = temporary.resolve("aliases.zip");
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(archive.toFile()))
        {
            output.setEncoding("GBK");
            for (String[] names : entries)
            {
                ZipArchiveEntry entry = new ZipArchiveEntry(names[0]);
                entry.addExtraField(new UnicodePathExtraField(names[1], names[0].getBytes(StandardCharsets.US_ASCII)));
                output.putArchiveEntry(entry);
                output.write("test data".getBytes(StandardCharsets.US_ASCII));
                output.closeArchiveEntry();
            }
        }
        return archive;
    }

    private int unicodeExtraOffset(byte[] content, boolean central)
    {
        ByteBuffer data = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        int header = central ? findSignature(content, 0x02014b50) : 0;
        int cursor = header + (central ? 46 : 30)
            + Short.toUnsignedInt(data.getShort(header + (central ? 28 : 26)));
        int end = cursor + Short.toUnsignedInt(data.getShort(header + (central ? 30 : 28)));
        while (cursor + 4 <= end)
        {
            if (Short.toUnsignedInt(data.getShort(cursor)) == 0x7075)
            {
                return cursor;
            }
            cursor += 4 + Short.toUnsignedInt(data.getShort(cursor + 2));
        }
        throw new AssertionError("Fixture Unicode extra field missing");
    }

    private void assertRejected(byte[] content) throws Exception
    {
        Path archive = Files.write(temporary.resolve("broken.zip"), content);
        assertThrows(ServiceException.class, () -> service.validateUploadedArchiveFile(archive, "zip"));
    }

    private int findSignature(byte[] content, int signature)
    {
        ByteBuffer data = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index <= content.length - 4; index++)
        {
            if (data.getInt(index) == signature)
            {
                return index;
            }
        }
        throw new AssertionError("Fixture signature missing");
    }
}
