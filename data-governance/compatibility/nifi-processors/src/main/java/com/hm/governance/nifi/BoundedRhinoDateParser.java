package com.hm.governance.nifi;

import java.time.ZoneId;
import java.util.Objects;
import java.util.TimeZone;
import java.util.function.Supplier;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * Fixed official Rhino version compared with Date vectors from the supplied tool package.
 * This is a private native Date.parse adapter, not a script engine or a general JS sandbox.
 * No user source, object, expression, scope or function is accepted or returned.
 */
final class BoundedRhinoDateParser {
    static final int MAX_TEXT_LENGTH = 256;
    private static final Object NATIVE_LOCK = new Object();
    // The processor captures this at class initialization, before any legacy date operation runs.
    // NativeDate itself caches JVM timezone globally within this NAR's Rhino class loader.
    private static final TimeZone STARTUP_ZONE = (TimeZone) TimeZone.getDefault().clone();
    private static final String[] FORBIDDEN_GLOBALS = {
        "Packages", "java", "javax", "org", "com", "edu", "net", "JavaAdapter", "JavaImporter", "getClass", "eval", "Function"
    };
    private final Supplier<TimeZone> currentZone;
    private final ContextFactory contextFactory = new ContextFactory();
    private ScriptableObject scope;
    private Scriptable dateConstructor;
    private Function parseFunction;

    static final class ZoneException extends IllegalArgumentException {
        ZoneException(String message) { super(message); }
    }

    BoundedRhinoDateParser() { this(TimeZone::getDefault); }
    BoundedRhinoDateParser(Supplier<TimeZone> currentZone) { this.currentZone = Objects.requireNonNull(currentZone); }

    static String engineZoneId() { return STARTUP_ZONE.toZoneId().getId(); }

    Long parse(String text, String requestedZone) {
        if (text == null) throw new IllegalArgumentException("String date required");
        if (text.length() > MAX_TEXT_LENGTH) throw new IllegalArgumentException("Legacy date text limit");
        synchronized (NATIVE_LOCK) {
            checkZone(requestedZone);
            if (Context.getCurrentContext() != null) throw new IllegalStateException("Nested Rhino context is not supported");
            Context context = contextFactory.enterContext();
            try {
                context.setOptimizationLevel(-1);
                context.setClassShutter(name -> false);
                context.getWrapFactory().setJavaPrimitiveWrap(false);
                if (scope == null) initializeScope(context);
                Object result = parseFunction.call(context, scope, dateConstructor, new Object[]{text});
                checkZone(requestedZone);
                double millis = ((Number) result).doubleValue();
                // Original scripts use new Date(Date.parse(text)).getTime(); apply its TimeClip domain.
                if (!Double.isFinite(millis) || Math.abs(millis) > 8.64e15) return null;
                return (long) millis;
            } finally { Context.exit(); }
        }
    }

    private void checkZone(String requested) {
        if (requested == null || requested.length() > 128) throw new IllegalArgumentException("Legacy date zone required");
        ZoneId zone = ZoneId.of(requested);
        TimeZone current = currentZone.get();
        if (!zone.normalized().equals(STARTUP_ZONE.toZoneId().normalized()))
            throw new ZoneException("RHINO_DATE_ZONE_MISMATCH: requires engine timezone " + engineZoneId());
        if (current == null || !current.getID().equals(STARTUP_ZONE.getID()) || !current.hasSameRules(STARTUP_ZONE))
            throw new ZoneException("RHINO_DATE_ZONE_DRIFT: engine timezone changed after initialization");
    }

    private void initializeScope(Context context) {
        // 1.7R3 predates initSafeStandardObjects. No evaluation occurs, and every Java bridge is removed.
        ScriptableObject initialized = context.initStandardObjects(null, false);
        for (String name : FORBIDDEN_GLOBALS) ScriptableObject.deleteProperty(initialized, name);
        for (String name : FORBIDDEN_GLOBALS) {
            if (ScriptableObject.hasProperty(initialized, name)) throw new IllegalStateException("Unexpected Java bridge");
        }
        Scriptable constructor = (Scriptable) ScriptableObject.getProperty(initialized, "Date");
        Function nativeParse = (Function) ScriptableObject.getProperty(constructor, "parse");
        if (!nativeParse.getClass().getName().equals("org.mozilla.javascript.IdFunctionObject"))
            throw new IllegalStateException("Native date function required");
        ((ScriptableObject) nativeParse).sealObject();
        ((ScriptableObject) constructor).sealObject();
        initialized.sealObject();
        dateConstructor = constructor;
        parseFunction = nativeParse;
        scope = initialized;
    }
}
