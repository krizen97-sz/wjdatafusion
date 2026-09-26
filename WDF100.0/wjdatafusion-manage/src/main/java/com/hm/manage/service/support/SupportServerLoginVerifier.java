package com.hm.manage.service.support;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import com.hm.manage.domain.vo.SupportServerLoginCheckVo;
import com.hm.manage.domain.vo.SupportServerLoginCheckVo.Step;

/** One bounded password authentication and a fixed, read-only UID probe; never a remote command API. */
@Component
public class SupportServerLoginVerifier
{
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int COMMAND_TIMEOUT = 15000;
    private static final int OUTPUT_LIMIT = 16384;
    private static final Pattern PASSWORD_PROMPT = Pattern.compile("(?is).*(?:password|密码)[：:]\\s*$");

    public SupportServerLoginCheckVo verify(String host, int port, String username, String password,
        String rootPassword, boolean direct, String expectedFingerprint)
    {
        List<Step> steps = new ArrayList<>();
        FingerprintRepository keys = new FingerprintRepository(expectedFingerprint);
        Session session = null;
        String stage = "SSH登录";
        try
        {
            session = createSession(host, port, username, password, keys);
            session.connect(CONNECT_TIMEOUT);
            if (!(session.getUserInfo() instanceof PasswordUserInfo info) || !info.prompted)
                return result("PASSWORD_NOT_VERIFIED", "SSH未执行密码认证，无法判定登记密码是否正确",
                    direct, username, host, port, keys, false, steps, stage, "warning");
            steps.add(new Step("SSH登录", "success", username + " 账号已登录"));
            stage = "身份确认";
            String marker = "__RY_" + UUID.randomUUID().toString().replace("-", "") + "_";
            Probe identity = execute(session, "LC_ALL=C; export LC_ALL; printf '" + marker + "%s__\\n' \"$(id -u)\"", marker, null);
            if (identity.uid == null)
                return result("IDENTITY_FAILED", "登录成功，但无法确认登录用户身份", direct, username, host, port, keys, false, steps, stage, "error");
            if (direct)
                return result(identity.uid == 0 ? "SUCCESS" : "NOT_ROOT",
                    identity.uid == 0 ? "root SSH登录和身份校验通过" : "登录账号未取得root身份",
                    true, username, host, port, keys, identity.uid == 0, steps, stage, identity.uid == 0 ? "success" : "error");
            if (identity.uid == 0)
                return result("PASSWORD_NOT_VERIFIED", "HIK账号已是UID 0，无法通过免密切换证明root密码正确",
                    false, username, host, port, keys, false, steps, stage, "warning");
            steps.add(new Step(stage, "success", "已确认普通用户身份"));
            stage = "切换root";
            // A PTY is required by common su/PAM policies. Disable echo before waiting for its password prompt.
            String command = "LC_ALL=C; export LC_ALL; stty -echo || exit 90; su - root -c 'printf \"" + marker + "%s__\\n\" \"$(id -u)\"'";
            Probe root = execute(session, command, marker, rootPassword);
            if (root.uid != null && root.uid == 0 && root.passwordSent)
                return result("SUCCESS", "HIK登录、root密码验证和root身份确认均通过", false, username, host, port, keys, true, steps, stage, "success");
            if (root.uid != null && root.uid == 0)
                return result("PASSWORD_NOT_VERIFIED", "已获得root权限，但设备未要求密码，无法判定登记的root密码是否正确",
                    false, username, host, port, keys, false, steps, stage, "warning");
            return result("SU_REJECTED", "HIK登录成功，root切换未通过；请检查root密码、账号状态及su/PAM策略",
                false, username, host, port, keys, false, steps, stage, "error");
        }
        catch (Exception error)
        {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            String code;
            String message;
            if (keys.fingerprint != null && !keys.accepted)
            {
                code = expectedFingerprint == null || expectedFingerprint.isBlank() ? "HOST_KEY_REQUIRED" : "HOST_KEY_CHANGED";
                message = "HOST_KEY_REQUIRED".equals(code) ? "请核对SSH主机指纹后再进行登录校验" : "SSH主机指纹不一致，已停止登录且未发送密码";
                stage = "主机身份";
            }
            else
            {
                String detail = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
                code = detail.contains("auth fail") || detail.contains("auth cancel") ? "AUTH_REJECTED"
                    : detail.contains("algorithm negotiation") ? "ALGORITHM_UNSUPPORTED"
                    : error instanceof java.net.SocketTimeoutException || detail.contains("timeout") || detail.contains("timed out") ? "TIMEOUT"
                    : error instanceof OutputLimitException ? "OUTPUT_LIMIT" : "CHECK_FAILED";
                message = switch (code)
                {
                    case "AUTH_REJECTED" -> "SSH拒绝登录；请核对账号密码、账号状态及SSH登录策略";
                    case "ALGORITHM_UNSUPPORTED" -> "SSH算法不兼容，不能据此判断密码错误；请联系管理员检查SSH兼容性";
                    case "TIMEOUT" -> "校验超时；请检查网络、SSH端口及远端登录提示";
                    case "OUTPUT_LIMIT" -> "远端输出超出校验限制，已断开连接";
                    default -> "连接或校验未完成；请检查网络、SSH服务和账号策略";
                };
            }
            return result(code, message, direct, username, host, port, keys, false, steps, stage, "HOST_KEY_REQUIRED".equals(code) ? "info" : "error");
        }
        finally
        {
            if (session != null)
            {
                session.disconnect();
                if (session.getUserInfo() instanceof PasswordUserInfo info) info.password = null;
                session.setUserInfo(null);
            }
        }
    }

    protected Session createSession(String host, int port, String username, String password, HostKeyRepository keys) throws JSchException
    {
        JSch client = new JSch();
        client.setHostKeyRepository(keys);
        Session session = client.getSession(username, host, port);
        session.setUserInfo(new PasswordUserInfo(password));
        session.setConfig("StrictHostKeyChecking", "yes");
        session.setConfig("PreferredAuthentications", "password");
        session.setConfig("MaxAuthTries", "1");
        session.setTimeout(CONNECT_TIMEOUT);
        return session;
    }

    Probe execute(Session session, String command, String marker, String password) throws Exception
    {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        try
        {
            channel.setCommand(command);
            channel.setPty(password != null);
            channel.setPtyType("vt100");
            InputStream input = channel.getInputStream();
            InputStream errors = channel.getErrStream();
            OutputStream output = channel.getOutputStream();
            channel.connect(CONNECT_TIMEOUT);
            long deadline = System.nanoTime() + COMMAND_TIMEOUT * 1000000L;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            boolean sent = false;
            while (true)
            {
                readAvailable(input, buffer);
                readAvailable(errors, buffer);
                String text = buffer.toString(StandardCharsets.UTF_8);
                if (password != null && !sent && PASSWORD_PROMPT.matcher(text.replaceAll("\\x1B\\[[0-9;]*[a-zA-Z]", "")).matches())
                {
                    output.write((password + "\n").getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    sent = true;
                }
                if (channel.isClosed() && input.available() == 0 && errors.available() == 0)
                {
                    var match = Pattern.compile(Pattern.quote(marker) + "(\\d+)__").matcher(text);
                    return new Probe(channel.getExitStatus() == 0 && match.find() ? Integer.valueOf(match.group(1)) : null, sent);
                }
                if (System.nanoTime() >= deadline) throw new java.net.SocketTimeoutException("SSH check timeout");
                Thread.sleep(30);
            }
        }
        finally { channel.disconnect(); }
    }

    private void readAvailable(InputStream input, ByteArrayOutputStream output) throws Exception
    {
        byte[] bytes = new byte[1024];
        while (input.available() > 0)
        {
            int length = input.read(bytes);
            if (length <= 0) break;
            if (output.size() + length > OUTPUT_LIMIT) throw new OutputLimitException();
            output.write(bytes, 0, length);
        }
    }

    private SupportServerLoginCheckVo result(String status, String message, boolean direct, String username,
        String host, int port, FingerprintRepository keys, boolean verified, List<Step> steps, String stage, String stepStatus)
    {
        steps.add(new Step(stage, stepStatus, message));
        return new SupportServerLoginCheckVo(status, message, direct ? "ROOT_DIRECT" : "HIK_SU_ROOT", username,
            host, port, keys.fingerprint, verified, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), List.copyOf(steps));
    }

    record Probe(Integer uid, boolean passwordSent) {}
    private static class OutputLimitException extends Exception {}

    static class PasswordUserInfo implements UserInfo
    {
        private String password;
        boolean prompted;
        PasswordUserInfo(String password) { this.password = password; }
        public String getPassword() { return password; }
        public boolean promptPassword(String message)
        {
            if (prompted) return false;
            prompted = true;
            return true;
        }
        public String getPassphrase() { return null; }
        public boolean promptPassphrase(String message) { return false; }
        public boolean promptYesNo(String message) { return false; }
        public void showMessage(String message) {}
    }

    static class FingerprintRepository implements HostKeyRepository
    {
        private final String expected;
        String fingerprint;
        boolean accepted;
        FingerprintRepository(String expected) { this.expected = expected; }
        public int check(String host, byte[] key)
        {
            try
            {
                fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key));
                accepted = fingerprint.equals(expected);
                return accepted ? OK : NOT_INCLUDED;
            }
            catch (Exception e) { return NOT_INCLUDED; }
        }
        public void add(HostKey key, UserInfo info) {}
        public void remove(String host, String type) {}
        public void remove(String host, String type, byte[] key) {}
        public String getKnownHostsRepositoryID() { return "Explicit device fingerprint confirmation"; }
        public HostKey[] getHostKey() { return new HostKey[0]; }
        public HostKey[] getHostKey(String host, String type) { return new HostKey[0]; }
    }
}
