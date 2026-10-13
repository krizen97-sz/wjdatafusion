package com.rynew.detl.bridge;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;

/** Reject unsafe existing paths; never chmod someone else's files. */
final class PrivateFiles {
    static final String REPLAY_MARKER = ".rynew-identity-bridge";
    private static final String REPLAY_OWNER = "rynew-detl-identity-bridge-v1\n";
    static Path absolute(String text) throws IOException {
        if (text == null || text.isBlank()) throw new IOException("Private configuration is required");
        Path path = Path.of(text);
        if (!path.isAbsolute() || !path.equals(path.normalize()) || path.getNameCount() < 3) throw new IOException("Private path is invalid");
        for (Path current = path; current != null; current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new IOException("Symbolic paths are not accepted");
        return path;
    }
    static void requirePrivate(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Private file is unavailable");
        var owner = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
        if (!Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).equals(owner)
            || Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).stream().anyMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_")))
            throw new IOException("Private file owner or mode is invalid");
    }
    static byte[] read(String configuredPath, int maximum) throws IOException {
        Path path = absolute(configuredPath); requirePrivate(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Private configuration is unavailable");
        try (var stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = stream.readNBytes(maximum + 1);
            if (bytes.length > maximum) throw new IOException("Private configuration exceeds its limit");
            return bytes;
        }
    }
    static Path replayDirectory(String configuredPath) throws IOException {
        Path root = absolute(configuredPath);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(root, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        requirePrivate(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Replay directory is unavailable");
        Path marker = root.resolve(REPLAY_MARKER);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            try (var entries = Files.newDirectoryStream(root)) {
                if (entries.iterator().hasNext()) throw new IOException("Replay directory is not owned by this module");
            }
            try { Files.createFile(marker, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); Files.writeString(marker, REPLAY_OWNER, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS); }
            catch (FileAlreadyExistsException concurrentOwner) { /* Another instance must have written the same marker. */ }
        }
        if (!REPLAY_OWNER.equals(new String(read(marker.toString(), 100), java.nio.charset.StandardCharsets.UTF_8))) throw new IOException("Replay directory owner is invalid");
        try (var entries = Files.newDirectoryStream(root)) {
            for (Path entry : entries) {
                if (entry.getFileName().toString().equals(REPLAY_MARKER)) continue;
                if (!entry.getFileName().toString().matches("[A-Za-z0-9_-]{43}\\.nonce") || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Replay directory has unrelated contents");
                requirePrivate(entry);
            }
        }
        return root;
    }
    private PrivateFiles() { }
}
