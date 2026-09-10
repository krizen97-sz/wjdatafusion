package com.hm.governance.nifi;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;

class BoundedRhinoDateParserTest {
    private String zone() { return BoundedRhinoDateParser.engineZoneId(); }
    private String otherZone() { return ZoneId.of(zone()).normalized().equals(ZoneOffset.UTC) ? "Asia/Shanghai" : "UTC"; }
    @Test void nativeParserReproducesObservedPermissiveDateForms() {
        var parser = new BoundedRhinoDateParser();
        long march = LocalDateTime.of(2020, 3, 1, 12, 0).atZone(ZoneId.of(zone())).toInstant().toEpochMilli();
        long january = LocalDateTime.of(2020, 1, 1, 0, 0).atZone(ZoneId.of(zone())).toInstant().toEpochMilli();
        assertEquals(march, parser.parse("2020/02/30 12:00:00", zone()));
        assertEquals(january, parser.parse("2020/01/01 00:00", zone()));
        assertEquals(january, parser.parse("2020/01/01", zone()));
        assertNull(parser.parse("invalid-date", zone())); assertNull(parser.parse("", zone()));
        assertNull(parser.parse("999999/01/01 00:00:00", zone()));
    }
    @Test void requestedZoneMustMatchTheCapturedEngineZone() {
        var parser = new BoundedRhinoDateParser(); String before = TimeZone.getDefault().getID();
        var error = assertThrows(BoundedRhinoDateParser.ZoneException.class, () -> parser.parse("2020/01/01", otherZone()));
        assertTrue(error.getMessage().startsWith("RHINO_DATE_ZONE_MISMATCH"));
        assertEquals(before, TimeZone.getDefault().getID()); assertNotNull(parser.parse("2020/01/01", zone()));
    }
    @Test void runtimeTimezoneDriftIsRejectedWithoutChangingGlobalTimezone() {
        String before = TimeZone.getDefault().getID();
        var current = new AtomicReference<>((TimeZone) TimeZone.getDefault().clone());
        var parser = new BoundedRhinoDateParser(current::get);
        assertNotNull(parser.parse("2020/01/01", zone()));
        current.set(TimeZone.getTimeZone(otherZone()));
        assertTrue(assertThrows(BoundedRhinoDateParser.ZoneException.class, () -> parser.parse("invalid-date", zone())).getMessage().startsWith("RHINO_DATE_ZONE_DRIFT"));
        var sameIdChangedRules = (TimeZone) TimeZone.getDefault().clone(); sameIdChangedRules.setRawOffset(sameIdChangedRules.getRawOffset() + 60000);
        current.set(sameIdChangedRules);
        assertThrows(BoundedRhinoDateParser.ZoneException.class, () -> parser.parse("2020/01/01", zone()));
        assertEquals(before, TimeZone.getDefault().getID());
    }
    @Test void sourceLookingStringsAreNeverEvaluated() {
        var parser = new BoundedRhinoDateParser(); String marker = "governance.rhino.native-date.probe";
        assertNull(System.getProperty(marker));
        assertNull(parser.parse("Packages.java.lang.System.setProperty('" + marker + "','executed')", zone()));
        assertNull(parser.parse("Date.now()", zone()));
        assertNull(parser.parse("(()=>{throw 1})()", zone()));
        assertNull(System.getProperty(marker)); assertNull(Context.getCurrentContext());
    }
    @Test void invalidTypeBudgetAndNestedContextsDoNotLeakRhinoState() {
        var parser = new BoundedRhinoDateParser();
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null, zone()));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("x".repeat(257), zone()));
        Context.enter();
        try { assertThrows(IllegalStateException.class, () -> parser.parse("2020/01/01", zone())); }
        finally { Context.exit(); }
        assertNotNull(parser.parse("2020/01/01", zone())); assertNull(Context.getCurrentContext());
    }
    @Test void concurrentCallsHaveStableResultsAndReleaseEveryThreadContext() throws Exception {
        var parser = new BoundedRhinoDateParser(); Long expected = parser.parse("2020/02/30 12:00:00", zone());
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) calls.add(pool.submit(() -> {
                for (int n = 0; n < 200; n++) if (!expected.equals(parser.parse("2020/02/30 12:00:00", zone())) || Context.getCurrentContext() != null) return false;
                return true;
            }));
            for (var call : calls) assertTrue(call.get());
        } finally { pool.shutdownNow(); }
    }
}
