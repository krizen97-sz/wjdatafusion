package com.hm.manage.service.support;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.hm.manage.domain.vo.SupportServerLoginCheckVo;
import com.hm.manage.service.support.SupportServerPasswordPoolMatcher.Password;

class SupportServerPasswordPoolMatcherTest
{
    private final List<Password> pool = List.of(new Password(1L,"A","first"),new Password(2L,"B","second"),new Password(3L,"C","third"));

    @Test void independentlyMatchesHikAndRootWithoutRepeatingRejectedHikCandidates() throws Exception
    {
        List<String> attempts = new ArrayList<>();
        var match = SupportServerPasswordPoolMatcher.match(pool,"HIK_SU_ROOT",3,(hik,root) -> {
            attempts.add(hik.name()+"/"+root.name());
            return result(!hik.value().equals("second") ? "AUTH_REJECTED" : root.value().equals("third") ? "SUCCESS" : "SU_REJECTED");
        });
        assertEquals(List.of("A/A","B/A","B/B","B/C"),attempts);
        assertTrue(match.success()); assertEquals(pool.get(1),match.hik()); assertEquals(pool.get(2),match.root());
        assertFalse(match.toString().contains("second")); assertFalse(pool.get(0).toString().contains("first"));
    }

    @Test void usesSamePasswordForBothAccountsWhenItMatches() throws Exception
    {
        AtomicInteger attempts = new AtomicInteger();
        var match = SupportServerPasswordPoolMatcher.match(pool,"AUTO",3,(hik,root) -> { attempts.incrementAndGet(); return result("SUCCESS"); });
        assertTrue(match.success()); assertEquals(1,attempts.get()); assertEquals(match.root(),match.hik());
    }

    @Test void autoFallsBackToRootAfterHikCandidatesAreExhausted() throws Exception
    {
        List<String> attempts = new ArrayList<>();
        var match = SupportServerPasswordPoolMatcher.match(pool,"AUTO",2,(hik,root) -> {
            attempts.add(hik == null ? "root:"+root.name() : "hik:"+hik.name());
            return result(hik == null && root.id()==2 ? "SUCCESS" : "AUTH_REJECTED");
        });
        assertEquals(List.of("hik:A","hik:B","root:A","root:B"),attempts);
        assertTrue(match.success()); assertNull(match.hik());
    }

    @Test void rootOnlyNeverAttemptsHikAndDuplicatesDoNotConsumeBudget() throws Exception
    {
        var duplicated = List.of(pool.get(0),new Password(4L,"duplicate","first"),pool.get(1));
        List<Long> attempts = new ArrayList<>();
        var match = SupportServerPasswordPoolMatcher.match(duplicated,"ROOT_DIRECT",2,(hik,root) -> {
            assertNull(hik); attempts.add(root.id()); return result(root.id()==2 ? "SUCCESS" : "AUTH_REJECTED");
        });
        assertTrue(match.success()); assertEquals(List.of(1L,2L),attempts);
    }

    @Test void passwordsAreNotTrimmedOrCaseFolded() throws Exception
    {
        var values = List.of(new Password(1L,"A"," secret "),new Password(2L,"B","secret"),new Password(3L,"C","Secret"));
        List<String> attempts = new ArrayList<>();
        SupportServerPasswordPoolMatcher.match(values,"ROOT_DIRECT",3,(hik,root) -> { attempts.add(root.value()); return result("AUTH_REJECTED"); });
        assertEquals(List.of(" secret ","secret","Secret"),attempts);
    }

    @Test void budgetDoesNotSilentlyExpandToTheEntirePool() throws Exception
    {
        AtomicInteger attempts = new AtomicInteger();
        var match = SupportServerPasswordPoolMatcher.match(pool,"AUTO",1,(hik,root) -> { attempts.incrementAndGet(); return result("AUTH_REJECTED"); });
        assertFalse(match.success()); assertEquals(2,attempts.get());
    }

    @Test void rejectedSuDoesNotFallBackToRootSshOrAnotherHikPassword() throws Exception
    {
        List<String> attempts = new ArrayList<>();
        var match = SupportServerPasswordPoolMatcher.match(pool,"AUTO",3,(hik,root) -> {
            assertNotNull(hik); attempts.add(hik.name()+"/"+root.name()); return result("SU_REJECTED");
        });
        assertFalse(match.success()); assertEquals(List.of("A/A","A/B","A/C"),attempts);
    }

    @Test void changedHostTimeoutPasswordlessSuAndUnverifiedSuccessStopImmediately() throws Exception
    {
        for (String status : List.of("HOST_KEY_CHANGED","TIMEOUT","PASSWORD_NOT_VERIFIED","IDENTITY_FAILED","ALGORITHM_UNSUPPORTED"))
        {
            AtomicInteger attempts = new AtomicInteger();
            var match = SupportServerPasswordPoolMatcher.match(pool,"AUTO",3,(hik,root) -> { attempts.incrementAndGet(); return result(status); });
            assertFalse(match.success()); assertEquals(1,attempts.get());
        }
        var match = SupportServerPasswordPoolMatcher.match(pool,"AUTO",3,(hik,root) ->
            new SupportServerLoginCheckVo("SUCCESS","not verified","HIK_SU_ROOT","hik","fixture",22,"fingerprint",false,"now",List.of()));
        assertFalse(match.success());
    }

    @Test void cancellationInterruptsTheSequenceWithoutFallback()
    {
        assertThrows(CancellationException.class,()->SupportServerPasswordPoolMatcher.match(pool,"AUTO",3,(hik,root) -> { throw new CancellationException(); }));
    }

    @Test void invalidLimitsAndControlCharactersCannotReachAuthentication()
    {
        var attempt = (SupportServerPasswordPoolMatcher.Attempt)(hik,root) -> { fail("No login expected"); return null; };
        assertThrows(IllegalArgumentException.class,()->SupportServerPasswordPoolMatcher.match(pool,"AUTO",4,attempt));
        assertThrows(IllegalArgumentException.class,()->SupportServerPasswordPoolMatcher.match(pool,"UNKNOWN",1,attempt));
        assertThrows(IllegalArgumentException.class,()->SupportServerPasswordPoolMatcher.match(List.of(new Password(1L,"x","bad\n")), "AUTO",1,attempt));
    }

    private SupportServerLoginCheckVo result(String status)
    { return new SupportServerLoginCheckVo(status,status,"HIK_SU_ROOT","hik","fixture",22,"fingerprint","SUCCESS".equals(status),"now",List.of()); }
}
