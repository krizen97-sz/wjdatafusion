package com.hm.manage.service.impl;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.bo.SupportServerScanBo;
import com.hm.manage.service.ISupportPlatformService;
import com.hm.manage.service.support.*;

@Service
public class SupportServerScanService
{
    private static final Logger log = LoggerFactory.getLogger(SupportServerScanService.class);
    private static final Set<String> RUNNING = Set.of("DISCOVERING", "SCANNING", "STOPPING");
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService coordinators = Executors.newFixedThreadPool(2, threads("server-scan-coordinator-"));
    private final ExecutorService workers = Executors.newFixedThreadPool(4, threads("server-scan-worker-"));
    private final SupportServerScanProbe probe;
    private final SupportServerLoginVerifier verifier;
    private final SupportServerLoginGuard guard;
    private final SupportServerScanCredentialService credentials;
    private final CredentialCryptoService crypto;
    private final SupportEquipmentWorkbookService workbook;
    private final SupportServerScanSyncService sync;
    private final ISupportPlatformService platforms;

    public SupportServerScanService(SupportServerScanProbe probe, SupportServerLoginVerifier verifier, SupportServerLoginGuard guard,
        SupportServerScanCredentialService credentials, CredentialCryptoService crypto, SupportEquipmentWorkbookService workbook,
        SupportServerScanSyncService sync, ISupportPlatformService platforms)
    {
        this.probe = probe; this.verifier = verifier; this.guard = guard; this.credentials = credentials;
        this.crypto = crypto; this.workbook = workbook; this.sync = sync; this.platforms = platforms;
    }

    public synchronized Map<String, Object> start(SupportServerScanBo.Start command, Long userId, String actor)
    {
        if (command == null || command.siteId() == null || command.platformId() == null) throw new ServiceException("请选择现场及新服务器所属子平台");
        SupportPlatform platform = platforms.selectSupportPlatformByPlatformId(command.platformId());
        if (platform == null || !command.siteId().equals(platform.getSiteId()) || !"SUB".equals(platform.getPlatformLevel()))
            throw new ServiceException("只能选择当前现场的子平台");
        int limit = command.attemptLimit() == null ? 1 : command.attemptLimit();
        if (limit < 1 || limit > 3) throw new ServiceException("每个账号最多尝试1至3个不同密码");
        if (!Set.of("AUTO", "ROOT_DIRECT", "HIK_SU_ROOT").contains(Objects.toString(command.loginMode(), "")))
            throw new ServiceException("请选择密码池登录策略；旧页面请刷新后重试");
        List<String> hosts = SupportServerScanTargets.expand(command.targets());
        List<Integer> ports = SupportServerScanTargets.ports(command.ports());
        List<Map<String, Object>> selected = credentials.selected(command.siteId(), command.credentialIds());
        prune();
        if (jobs.size() >= 50 || jobs.values().stream().filter(job -> RUNNING.contains(job.state)).count() >= 2)
            throw new ServiceException("扫描任务繁忙，请等待其他任务结束");
        if (jobs.values().stream().anyMatch(job -> job.siteId.equals(command.siteId()) && (RUNNING.contains(job.state) || "REVIEW".equals(job.state))))
            throw new ServiceException("该现场已有扫描任务，请先结束或取消");
        Map<String, Object> snapshot = workbook.snapshot(command.siteId());
        Job job = new Job(command.siteId(), command.platformId(), userId, actor, ports, selected, limit, command.loginMode());
        Map<String, Map<String, Object>> existing = new HashMap<>();
        @SuppressWarnings("unchecked") List<Map<String, Object>> saved = (List<Map<String, Object>>) snapshot.get("rows");
        for (Map<String, Object> row : saved)
        {
            Set<String> addresses = new LinkedHashSet<>();
            addresses.add(SupportServerScanTargets.canonical(Objects.toString(row.get("ipAddress"), "")));
            if ("HARDWARE".equals(row.get("sourceType"))) addresses.add(SupportServerScanTargets.canonical(Objects.toString(row.get("manageIp"), "")));
            for (String address : addresses)
            {
                if (address.isEmpty()) continue;
                if (existing.put(address, row) != null && hosts.contains(address)) throw new ServiceException("当前现场同IP存在多条设备记录，请先处理或缩小扫描范围");
            }
        }
        for (String host : hosts) job.rows.put(host, new Target(host, existing.get(host)));
        jobs.put(job.id, job);
        execute(job, new ArrayList<>(job.rows.values()), target -> discover(job, target), "REVIEW", null);
        return job.view();
    }

    public Map<String, Object> status(String id, Long userId) { return owned(id, userId).view(); }
    public Map<String, Object> active(Long siteId, Long userId)
    {
        prune();
        return jobs.values().stream().filter(job -> job.userId.equals(userId) && job.siteId.equals(siteId))
            .max(Comparator.comparingLong(job -> job.created)).map(Job::view).orElse(null);
    }
    public Map<String, Object> cancel(String id, Long userId)
    {
        Job job = owned(id, userId);
        synchronized (job)
        {
            if (!RUNNING.contains(job.state) && !"REVIEW".equals(job.state)) return job.view();
            job.cancelled = true;
            job.state = RUNNING.contains(job.state) ? "STOPPING" : "CANCELLED";
            return job.view();
        }
    }

    public synchronized Map<String, Object> confirm(String id, Long userId, SupportServerScanBo.Confirm input)
    {
        Job job = owned(id, userId);
        if (input == null || !input.fingerprintsConfirmed() || input.targets() == null || input.targets().isEmpty() || input.targets().size() > 128)
            throw new ServiceException("请核对主机指纹并选择1至128台服务器");
        SecurityContext context = SecurityContextHolder.getContext();
        synchronized (job)
        {
            if (!"REVIEW".equals(job.state) || job.cancelled) throw new ServiceException("任务状态已变化，请刷新");
            if (jobs.values().stream().filter(item -> RUNNING.contains(item.state)).count() >= 2) throw new ServiceException("扫描任务繁忙，请稍后继续");
            List<Long> ids = job.candidates.stream().map(row -> ((Number) row.get("credentialId")).longValue()).toList();
            if (!sameCredentials(job.candidates, credentials.selected(job.siteId, ids))) throw new ServiceException("授权凭据池已变化，请重新发起扫描");
            Set<String> seen = new HashSet<>();
            List<Target> chosen = new ArrayList<>();
            for (var selection : input.targets())
            {
                if (selection == null || !seen.add(selection.address())) throw new ServiceException("目标重复或无效");
                Target target = job.rows.get(selection.address());
                if (target == null || !"READY".equals(target.state)) throw new ServiceException("目标不属于本次可核验的服务器");
                target.chosen = target.ports.stream().filter(port -> Objects.equals(port.port(), selection.port()) && "READY".equals(port.status())).findFirst()
                    .orElseThrow(() -> new ServiceException("所选端口没有已探测的SSH指纹"));
                if (target.original != null && (!Boolean.TRUE.equals(target.original.get("canEdit")) || !"0".equals(String.valueOf(target.original.get("status")))))
                    throw new ServiceException("已有服务器已停用或不可修改");
                chosen.add(target);
            }
            job.done = 0; job.total = chosen.size(); job.state = "SCANNING";
            for (Target target : job.rows.values()) target.state = seen.contains(target.address) ? "PENDING" : "SKIPPED";
            execute(job, chosen, target -> authenticate(job, target), "DONE", context);
            return job.view();
        }
    }

    private void discover(Job job, Target target)
    {
        List<SupportServerScanProbe.Port> result = new ArrayList<>();
        for (int port : job.ports)
        {
            if (job.cancelled || Thread.currentThread().isInterrupted()) return;
            result.add(probe.inspect(target.address, port));
        }
        synchronized (job)
        {
            target.ports = List.copyOf(result);
            target.state = result.stream().anyMatch(port -> "READY".equals(port.status())) ? "READY" : "NO_SSH";
            target.message = "READY".equals(target.state) ? "主机指纹待确认" : "没有可确认的SSH服务，未尝试密码";
            if (target.original != null && (!Boolean.TRUE.equals(target.original.get("canEdit")) || !"0".equals(String.valueOf(target.original.get("status")))))
            { target.state = "SKIPPED"; target.message = "已有服务器已停用或不可修改，跳过登录与同步"; }
            if (target.original != null && !"SERVER".equals(target.original.get("sourceType")))
            { target.state = "SKIPPED"; target.message = "已登记为硬件设备，避免重复新增为服务器"; }
        }
    }

    private void authenticate(Job job, Target target)
    {
        if (job.cancelled) return;
        synchronized (job) { target.stage = ScanStage.CONFIGURATION; }
        try (SupportServerLoginGuard.Lease lease = guard.acquire(target.address))
        {
            ensureCurrent(job);
            synchronized (job) { target.stage = ScanStage.DECRYPT; }
            var passwords = job.candidates.stream().map(row -> new SupportServerPasswordPoolMatcher.Password(
                ((Number) row.get("credentialId")).longValue(), String.valueOf(row.get("name")),
                crypto.decrypt(String.valueOf(row.get("passwordCipher"))))).toList();
            synchronized (job) { target.stage = ScanStage.PASSWORD_POOL; }
            String strategy = "AUTO".equals(job.loginMode) && target.original != null
                && Boolean.TRUE.equals(target.original.get("rootDirectLogin")) ? "ROOT_DIRECT" : job.loginMode;
            var match = SupportServerPasswordPoolMatcher.match(passwords, strategy, job.attemptLimit, (hikPassword, rootPassword) -> {
                if (target.attempts > 0) Thread.sleep(2000);
                ensureCurrent(job);
                boolean direct = hikPassword == null;
                synchronized (job)
                {
                    target.state = "VERIFYING"; target.attempts++; target.stage = ScanStage.AUTHENTICATE;
                    target.candidate = direct ? "root: " + rootPassword.name() : "hik: " + hikPassword.name() + " / root: " + rootPassword.name();
                }
                var login = verifier.verify(target.address, target.chosen.port(), direct ? "root" : "hik",
                    direct ? rootPassword.value() : hikPassword.value(), rootPassword.value(), direct, target.chosen.fingerprint());
                if (!Set.of("HOST_KEY_REQUIRED", "HOST_KEY_CHANGED").contains(login.status())) lease.attempted();
                synchronized (job) { target.message = login.message(); target.mode = login.mode(); }
                return login;
            });
            if (match.success())
            {
                synchronized (job) { target.rootPasswordVerified = true; target.stage = ScanStage.COLLECT; }
                ensureCurrent(job);
                boolean direct = match.hik() == null;
                String root = match.root().value(), hik = direct ? "" : match.hik().value();
                var facts = probe.collect(target.address, target.chosen.port(), direct ? "root" : "hik", direct ? root : hik, target.chosen.fingerprint());
                ensureCurrent(job);
                Long previousId = target.original == null ? null : ((Number) target.original.get("sourceId")).longValue();
                synchronized (job) { target.stage = ScanStage.SYNCHRONIZE; }
                Long serverId = sync.sync(job.siteId, job.platformId, target.address, previousId,
                    target.original == null ? null : String.valueOf(target.original.get("revision")), target.chosen.port(), direct,
                    hik, root, facts, target.ports.stream().filter(SupportServerScanProbe.Port::open).map(port -> String.valueOf(port.port())).reduce((a, b) -> a + "," + b).orElse(""), job.actor);
                synchronized (job)
                {
                    target.serverId = serverId; target.facts = facts.values(); target.state = "SYNCED";
                    target.message = (previousId == null ? "已新建服务器并归属选定子平台" : "已同步服务器，原有平台归属保持不变")
                        + (facts.warning().isEmpty() ? "" : "；" + facts.warning());
                }
                return;
            }
            synchronized (job)
            {
                target.state = "FAILED";
                target.errorCode = match.check().status();
                if (Set.of("AUTH_REJECTED", "SU_REJECTED").contains(match.check().status()))
                    target.message += "；已达到每账号尝试上限或候选密码已用尽，未同步";
            }
        }
        catch (Exception error)
        {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            synchronized (job)
            {
                target.state = job.cancelled ? "SKIPPED" : "FAILED";
                target.errorCode = job.cancelled ? "CANCELLED" : target.stage.errorCode;
                target.message = job.cancelled ? "任务已停止，本次未同步" : target.stage.failureMessage;
                if (!job.cancelled)
                {
                    target.diagnosticId = UUID.randomUUID().toString();
                    target.message += "（" + target.errorCode + "，编号：" + target.diagnosticId + "）";
                    // Exception messages/causes can contain SQL values or credentials; log metadata only.
                    String location = Arrays.stream(error.getStackTrace())
                        .filter(frame -> frame.getClassName().startsWith("com.hm."))
                        .findFirst().map(StackTraceElement::toString).orElse("unavailable");
                    log.warn("Server scan failed diagnostic={} task={} site={} stage={} type={} location={}",
                        target.diagnosticId, job.id, job.siteId, target.stage, error.getClass().getName(), location);
                }
            }
        }
    }

    private void ensureCurrent(Job job)
    {
        if (job.cancelled || Thread.currentThread().isInterrupted()) throw new CancellationException();
        List<Long> ids = job.candidates.stream().map(row -> ((Number) row.get("credentialId")).longValue()).toList();
        if (!sameCredentials(job.candidates, credentials.selected(job.siteId, ids)))
            throw new ServiceException("授权密码池已变化，已停止使用旧密码");
    }

    private void execute(Job job, List<Target> targets, Consumer<Target> action, String completed, SecurityContext security)
    {
        coordinators.submit(() -> {
            try
            {
                // Bound queued work as well as active connections, so cancellation does not wait behind a whole subnet.
                for (int offset = 0; offset < targets.size() && !job.cancelled; offset += 4)
                {
                    List<Future<?>> futures = new ArrayList<>();
                    for (Target target : targets.subList(offset, Math.min(offset + 4, targets.size())))
                    {
                        Runnable work = () -> {
                            try { if (!job.cancelled) action.accept(target); }
                            finally { synchronized (job) { job.done++; } }
                        };
                        futures.add(workers.submit(security == null ? work : new DelegatingSecurityContextRunnable(work, security)));
                    }
                    for (Future<?> future : futures) future.get();
                }
                synchronized (job)
                {
                    if (job.cancelled) for (Target target : targets)
                    {
                        if (Set.of("PENDING", "VERIFYING").contains(target.state)) { target.state = "SKIPPED"; target.message = "任务已停止，本次未同步"; }
                    }
                    job.state = job.cancelled ? "CANCELLED" : completed; job.finished = System.currentTimeMillis();
                }
            }
            catch (Exception error)
            {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                synchronized (job) { job.cancelled = true; job.state = "FAILED"; job.finished = System.currentTimeMillis(); job.error = "扫描任务未完成，请重新核对目标后重试"; }
            }
        });
    }
    private Job owned(String id, Long userId)
    {
        prune();
        Job job = jobs.get(id);
        if (job == null || !job.userId.equals(userId)) throw new ServiceException("任务不存在、已过期或不属于当前用户");
        return job;
    }
    private void prune()
    {
        long now = System.currentTimeMillis();
        jobs.entrySet().removeIf(entry -> !RUNNING.contains(entry.getValue().state)
            && now - Math.max(entry.getValue().created, entry.getValue().finished) > 30 * 60000L);
    }
    static boolean sameCredentials(List<Map<String, Object>> first, List<Map<String, Object>> second)
    {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) for (String key : List.of("credentialId", "passwordCipher"))
            if (!Objects.equals(first.get(i).get(key), second.get(i).get(key))) return false;
        return true;
    }
    private static ThreadFactory threads(String prefix)
    {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> { Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet()); thread.setDaemon(true); return thread; };
    }
    @PreDestroy public void close() { jobs.values().forEach(job -> job.cancelled = true); coordinators.shutdownNow(); workers.shutdownNow(); }

    private enum ScanStage
    {
        DISCOVERY("", ""),
        CONFIGURATION("SCAN_CONFIGURATION_FAILED", "扫描配置核对未通过，请检查授权密码池、任务状态或登录冷却限制；未发送本次密码"),
        DECRYPT("PASSWORD_DECRYPT_FAILED", "密码池解密失败，请核对原SUPPORT_AES_KEY及密码池数据；尚未尝试SSH登录"),
        PASSWORD_POOL("PASSWORD_POOL_INVALID", "密码池校验失败，候选密码为空或包含不支持的字符；尚未尝试SSH登录"),
        AUTHENTICATE("SSH_VERIFY_FAILED", "SSH登录或root核验执行异常，未同步数据，请按编号检查服务日志"),
        COLLECT("SYSTEM_INFO_FAILED", "SSH登录及root密码已核验通过，但系统资料采集或同步前检查失败；未同步数据"),
        SYNCHRONIZE("SERVER_SYNC_FAILED", "SSH登录及root密码已核验通过，但数据库同步失败；请检查数据库升级、写入权限及操作记录，本次同步未完成");

        final String errorCode, failureMessage;
        ScanStage(String errorCode, String failureMessage) { this.errorCode = errorCode; this.failureMessage = failureMessage; }
    }

    private static final class Target
    {
        final String address;
        final Map<String, Object> original;
        List<SupportServerScanProbe.Port> ports = List.of();
        SupportServerScanProbe.Port chosen;
        String state = "PENDING", message = "等待探测", candidate = "", mode = "";
        ScanStage stage = ScanStage.DISCOVERY;
        String errorCode = "", diagnosticId = "";
        boolean rootPasswordVerified;
        int attempts;
        Long serverId;
        Map<String, String> facts = Map.of();
        Target(String address, Map<String, Object> original) { this.address = address; this.original = original; }
        Map<String, Object> view()
        {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("address", address); result.put("existing", original != null); result.put("ports", ports);
            result.put("serverId", serverId == null && original != null && "SERVER".equals(original.get("sourceType")) ? original.get("sourceId") : serverId);
            result.put("serverName", original == null ? "" : original.get("assetName")); result.put("registeredPort", original == null ? null : original.get("sshPort"));
            result.put("state", state); result.put("message", message); result.put("attempts", attempts); result.put("candidate", candidate);
            result.put("mode", mode); result.put("facts", facts); result.put("port", chosen == null ? null : chosen.port());
            result.put("stage", stage.name()); result.put("rootPasswordVerified", rootPasswordVerified);
            result.put("errorCode", errorCode); result.put("diagnosticId", diagnosticId);
            return result;
        }
    }
    private static final class Job
    {
        final String id = UUID.randomUUID().toString(), actor, loginMode;
        final Long siteId, platformId, userId;
        final List<Integer> ports;
        final List<Map<String, Object>> candidates;
        final int attemptLimit;
        final long created = System.currentTimeMillis();
        final Map<String, Target> rows = new LinkedHashMap<>();
        volatile String state = "DISCOVERING";
        volatile boolean cancelled;
        volatile long finished;
        String error = "";
        int done, total;
        Job(Long siteId, Long platformId, Long userId, String actor, List<Integer> ports, List<Map<String, Object>> candidates, int limit, String loginMode)
        { this.siteId = siteId; this.platformId = platformId; this.userId = userId; this.actor = actor; this.ports = ports; this.candidates = candidates; this.attemptLimit = limit; this.loginMode = loginMode; }
        synchronized Map<String, Object> view()
        {
            Map<String, Object> view = new LinkedHashMap<>(Map.of("taskId", id, "siteId", siteId, "platformId", platformId, "state", state, "done", done,
                "total", total == 0 ? rows.size() : total, "error", error, "rows", rows.values().stream().map(Target::view).toList(),
                "createdAt", java.time.Instant.ofEpochMilli(created).toString()));
            view.put("loginMode", loginMode); view.put("attemptLimit", attemptLimit);
            return view;
        }
    }
}
