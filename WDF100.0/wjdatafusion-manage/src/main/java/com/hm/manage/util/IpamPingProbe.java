package com.hm.manage.util;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class IpamPingProbe
{
    public static final String STATUS_ONLINE = "ONLINE";
    public static final String STATUS_OFFLINE = "OFFLINE";
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    private static final long PROCESS_EXIT_GRACE_MS = 500L;

    public ProbeResult probe(String ipAddress, int timeoutMs)
    {
        long started = System.nanoTime();
        Process process = null;
        try
        {
            process = new ProcessBuilder(buildCommand(ipAddress, timeoutMs))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            boolean finished = process.waitFor(timeoutMs + PROCESS_EXIT_GRACE_MS, TimeUnit.MILLISECONDS);
            long elapsedMs = elapsedMillis(started);
            if (!finished)
            {
                process.destroyForcibly();
                return ProbeResult.offline(elapsedMs);
            }
            int exitCode = process.exitValue();
            if (exitCode == 0)
            {
                return ProbeResult.online(elapsedMs);
            }
            if (exitCode == 1)
            {
                return ProbeResult.offline(elapsedMs);
            }
            return ProbeResult.error(elapsedMs, "Ping命令返回异常状态：" + exitCode);
        }
        catch (IOException commandError)
        {
            return probeWithJavaFallback(ipAddress, timeoutMs, started, commandError);
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            if (process != null)
            {
                process.destroyForcibly();
            }
            return ProbeResult.error(elapsedMillis(started), "探测线程已中断");
        }
    }

    List<String> buildCommand(String ipAddress, int timeoutMs)
    {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (operatingSystem.contains("win"))
        {
            return List.of("ping", "-n", "1", "-w", String.valueOf(timeoutMs), ipAddress);
        }
        if (operatingSystem.contains("mac") || operatingSystem.contains("darwin"))
        {
            return List.of("ping", "-n", "-c", "1", "-W", String.valueOf(timeoutMs), ipAddress);
        }
        int timeoutSeconds = Math.max(1, (int) Math.ceil(timeoutMs / 1000.0));
        return List.of("ping", "-n", "-c", "1", "-W", String.valueOf(timeoutSeconds), ipAddress);
    }

    private ProbeResult probeWithJavaFallback(
        String ipAddress, int timeoutMs, long started, IOException commandError)
    {
        try
        {
            boolean reachable = InetAddress.getByName(ipAddress).isReachable(timeoutMs);
            long elapsedMs = elapsedMillis(started);
            return reachable ? ProbeResult.online(elapsedMs) : ProbeResult.offline(elapsedMs);
        }
        catch (IOException fallbackError)
        {
            String message = "Ping命令不可用，Java探测失败：" + fallbackError.getMessage();
            if (commandError.getMessage() != null && !commandError.getMessage().isBlank())
            {
                message = "Ping命令不可用：" + commandError.getMessage();
            }
            return ProbeResult.error(elapsedMillis(started), limit(message, 500));
        }
    }

    private long elapsedMillis(long started)
    {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    private String limit(String value, int maximumLength)
    {
        if (value == null || value.length() <= maximumLength)
        {
            return value;
        }
        return value.substring(0, maximumLength);
    }

    public static class ProbeResult
    {
        private final String status;
        private final Long responseTimeMs;
        private final String errorMessage;

        private ProbeResult(String status, Long responseTimeMs, String errorMessage)
        {
            this.status = status;
            this.responseTimeMs = responseTimeMs;
            this.errorMessage = errorMessage;
        }

        public static ProbeResult online(long responseTimeMs)
        {
            return new ProbeResult(STATUS_ONLINE, responseTimeMs, null);
        }

        public static ProbeResult offline(long responseTimeMs)
        {
            return new ProbeResult(STATUS_OFFLINE, responseTimeMs, null);
        }

        public static ProbeResult error(long responseTimeMs, String errorMessage)
        {
            return new ProbeResult(STATUS_UNKNOWN, responseTimeMs, errorMessage);
        }

        public String getStatus()
        {
            return status;
        }

        public Long getResponseTimeMs()
        {
            return responseTimeMs;
        }

        public String getErrorMessage()
        {
            return errorMessage;
        }
    }
}
