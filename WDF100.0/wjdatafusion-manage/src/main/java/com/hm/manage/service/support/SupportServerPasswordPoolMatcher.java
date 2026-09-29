package com.hm.manage.service.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.hm.manage.domain.vo.SupportServerLoginCheckVo;

/** Match independent authorized passwords without enumerating a Cartesian product. */
public final class SupportServerPasswordPoolMatcher
{
    private SupportServerPasswordPoolMatcher() {}

    public record Password(Long id, String name, String value)
    {
        @Override public String toString() { return "Password[redacted]"; }
    }
    public record Match(SupportServerLoginCheckVo check, Password hik, Password root)
    {
        public boolean success() { return "SUCCESS".equals(check.status()) && check.rootPasswordVerified(); }
        @Override public String toString() { return "Match[redacted]"; }
    }
    @FunctionalInterface public interface Attempt
    {
        SupportServerLoginCheckVo verify(Password hik, Password root) throws Exception;
    }

    public static Match match(List<Password> input, String mode, int limit, Attempt attempt) throws Exception
    {
        if (limit < 1 || limit > 3 || !List.of("AUTO", "ROOT_DIRECT", "HIK_SU_ROOT").contains(mode))
            throw new IllegalArgumentException("Invalid password pool limits or login mode");
        Map<String, Password> unique = new LinkedHashMap<>();
        for (Password password : input)
        {
            if (password.value() == null || password.value().isEmpty() || password.value().length() > 128
                || password.value().chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid authorized password");
            unique.putIfAbsent(password.value(), password);
        }
        List<Password> passwords = unique.values().stream().limit(limit).toList();
        if (passwords.isEmpty()) throw new IllegalArgumentException("Empty password pool");
        Match last = null;
        if (!"ROOT_DIRECT".equals(mode))
        {
            for (Password hik : passwords)
            {
                Password root = passwords.get(0);
                last = new Match(attempt.verify(hik, root), hik, root);
                if (last.success()) return last;
                if ("AUTH_REJECTED".equals(last.check().status())) continue;
                if (!"SU_REJECTED".equals(last.check().status())) return last;
                // hik is now proven: keep it fixed and try the remaining root candidates only.
                for (int index = 1; index < passwords.size(); index++)
                {
                    root = passwords.get(index);
                    last = new Match(attempt.verify(hik, root), hik, root);
                    if (last.success() || !"SU_REJECTED".equals(last.check().status())) return last;
                }
                return last;
            }
            if ("HIK_SU_ROOT".equals(mode)) return last;
        }
        // Automatic mode falls back only when all hik candidates were rejected at SSH login.
        for (Password root : passwords)
        {
            last = new Match(attempt.verify(null, root), null, root);
            if (last.success() || !"AUTH_REJECTED".equals(last.check().status())) return last;
        }
        return last;
    }
}
