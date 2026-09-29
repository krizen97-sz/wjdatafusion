package com.hm.manage.service.support;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;

@Component
public class SupportServerScanProbe
{
    private final SupportServerLoginVerifier verifier;
    public SupportServerScanProbe(SupportServerLoginVerifier verifier) { this.verifier = verifier; }
    public record Port(int port, boolean open, String status, String fingerprint, String message) {}
    public record Facts(String hostName, String osName, String osVersion, String kernelVersion, String warning)
    {
        public Map<String, String> values()
        {
            return Map.of("hostName", hostName, "osName", osName, "osVersion", osVersion, "kernelVersion", kernelVersion);
        }
        public String osType()
        {
            if (osName.isEmpty() || osVersion.isEmpty()) return "";
            String value = (osName + " " + osVersion).trim();
            return value.length() <= 64 ? value : osName.length() <= 64 ? osName : "";
        }
    }
    // Fixed read-only commands: never source os-release and never accept a command/path from the browser.
    static final String FACTS_COMMAND = "LC_ALL=C; export LC_ALL; "
        + "printf '__RY_HOST__\\n'; hostname 2>/dev/null; printf '__RY_KERNEL__\\n'; uname -r; "
        + "printf '__RY_OS__\\n'; cat /etc/os-release 2>/dev/null; printf '\\n__RY_LEGACY__\\n'; "
        + "cat /etc/centos-release /etc/redhat-release /etc/system-release 2>/dev/null | head -n 1; "
        + "printf '\\n__RY_END__\\n'";

    public Port inspect(String host, int port)
    {
        try (Socket socket = new Socket()) { socket.connect(new InetSocketAddress(host, port), 1500); }
        catch (Exception ignored) { return new Port(port, false, "CLOSED", null, "未连通或连接超时"); }
        var keys = new SupportServerLoginVerifier.FingerprintRepository(null);
        Session session = null;
        try
        {
            session = verifier.createSession(host, port, "root", null, keys);
            session.setTimeout(4000);
            session.connect(4000);
        }
        catch (Exception ignored) { /* An unknown key is deliberately rejected before authentication. */ }
        finally { if (session != null) { session.disconnect(); session.setUserInfo(null); } }
        return keys.fingerprint == null
            ? new Port(port, true, "UNSUPPORTED", null, "端口开放，但未取得可核验的SSH指纹")
            : new Port(port, true, "READY", keys.fingerprint, "SSH主机指纹待确认，尚未发送密码");
    }

    public Facts collect(String host, int port, String username, String password, String fingerprint)
    {
        Session session = null;
        boolean connected = false;
        try
        {
            var keys = new SupportServerLoginVerifier.FingerprintRepository(fingerprint);
            session = verifier.createSession(host, port, username, password, keys);
            session.connect(10000);
            connected = true;
            return parse(readFacts(session));
        }
        catch (Exception error)
        {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            if (!connected) throw new com.hm.common.exception.ServiceException("采集时SSH身份或连接未通过，已停止同步，请重新核对主机指纹及凭据");
            return new Facts("", "", "", "", "登录已验证，但系统资料采集未完成；原有版本资料保持不变");
        }
        finally { if (session != null) { session.disconnect(); session.setUserInfo(null); } }
    }

    String readFacts(Session session) throws Exception
    {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        try
        {
            channel.setCommand(FACTS_COMMAND);
            InputStream input = channel.getInputStream(), errors = channel.getErrStream();
            channel.connect(10000);
            long deadline = System.nanoTime() + 15000000000L;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            while (true)
            {
                read(input, buffer); read(errors, buffer);
                if (channel.isClosed() && input.available() == 0 && errors.available() == 0)
                {
                    if (channel.getExitStatus() != 0) throw new IllegalStateException("version query failed");
                    return buffer.toString(StandardCharsets.UTF_8);
                }
                if (System.nanoTime() > deadline) throw new java.net.SocketTimeoutException();
                Thread.sleep(30);
            }
        }
        finally { channel.disconnect(); }
    }
    private void read(InputStream input, ByteArrayOutputStream buffer) throws Exception
    {
        byte[] data = new byte[1024];
        while (input.available() > 0)
        {
            int size = input.read(data);
            if (size < 1) break;
            if (buffer.size() + size > 16384) throw new IllegalStateException("output limit");
            buffer.write(data, 0, size);
        }
    }
    public static Facts parse(String output)
    {
        String section = "", host = "", kernel = "", legacy = "";
        Map<String, String> release = new LinkedHashMap<>();
        for (String line : output.split("\\r?\\n"))
        {
            line = line.trim();
            if (line.startsWith("__RY_") && line.endsWith("__")) { section = line; continue; }
            if (line.isEmpty()) continue;
            if ("__RY_HOST__".equals(section) && host.isEmpty()) host = clean(line, 255);
            if ("__RY_KERNEL__".equals(section) && kernel.isEmpty()) kernel = clean(line, 120);
            if ("__RY_LEGACY__".equals(section) && legacy.isEmpty()) legacy = clean(line, 120);
            if ("__RY_OS__".equals(section))
            {
                int split = line.indexOf('=');
                if (split > 0)
                {
                    String key = line.substring(0, split), value = line.substring(split + 1);
                    if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))))
                        value = value.substring(1, value.length() - 1);
                    if (java.util.Set.of("NAME", "VERSION_ID", "VERSION", "PRETTY_NAME").contains(key)) release.put(key, clean(value, 120));
                }
            }
        }
        String name = release.getOrDefault("NAME", release.getOrDefault("PRETTY_NAME", legacy));
        String version = release.getOrDefault("VERSION_ID", release.getOrDefault("VERSION", ""));
        if (!legacy.isEmpty())
        {
            var match = java.util.regex.Pattern.compile("^(.+?) release ([0-9][A-Za-z0-9._-]*).*$").matcher(legacy);
            if (match.matches())
            {
                String legacyName = match.group(1).trim(), legacyVersion = match.group(2);
                boolean sameFamily = name.toLowerCase(java.util.Locale.ROOT).startsWith(legacyName.toLowerCase(java.util.Locale.ROOT))
                    || legacyName.toLowerCase(java.util.Locale.ROOT).startsWith(name.toLowerCase(java.util.Locale.ROOT));
                if (release.isEmpty()) { name = legacyName; version = legacyVersion; }
                else if (sameFamily && !version.isEmpty() && legacyVersion.startsWith(version + ".")) version = legacyVersion;
            }
        }
        boolean complete = !host.isEmpty() && !kernel.isEmpty() && !name.isEmpty() && !version.isEmpty();
        return new Facts(host, name, version, kernel, complete ? "" : "系统资料未读取完整，原有扫描版本资料保持不变");
    }
    private static String clean(String value, int max)
    {
        String clean = value.replaceAll("[\\p{Cntrl}]", "").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
