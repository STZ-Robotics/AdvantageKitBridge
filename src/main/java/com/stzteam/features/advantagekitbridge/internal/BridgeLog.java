package com.stzteam.features.advantagekitbridge.internal;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Console reporting for the bridge, with de-duplication.
 * <p>
 * Every diagnostic the bridge emits comes from a condition that repeats once per loop -- an
 * unsupported field type, an inaccessible field -- so each distinct message is reported only
 * the first time it happens. Without that guard a single unsupported field would print fifty
 * lines per second for the whole match.
 * <p>
 * Output goes to the standard streams rather than to {@code DriverStation}, matching what
 * MARS' {@code GCSConsole} and ForgeMini's {@code NetworkIO} already do, so the MARS Terminal
 * picks these up alongside the rest of the framework's output. It also keeps this path free
 * of the HAL, which means the serialisation layer can be exercised by plain unit tests.
 */
public final class BridgeLog {

    private static final String PREFIX = "[MARS/AdvantageKitBridge] ";
    private static final Set<String> seen = ConcurrentHashMap.newKeySet();

    private BridgeLog() {}

    /**
     * Reports a warning exactly once for a given de-duplication token.
     *
     * @param token   A stable identity for this warning (e.g. "ClassName.fieldName").
     * @param message The human-readable message.
     */
    public static void warnOnce(String token, String message) {
        if (seen.add(token)) {
            System.out.println(PREFIX + "WARNING: " + message);
        }
    }

    /**
     * Reports an error exactly once for a given de-duplication token.
     *
     * @param token   A stable identity for this error.
     * @param message The human-readable message.
     */
    public static void errorOnce(String token, String message) {
        if (seen.add(token)) {
            System.err.println(PREFIX + "ERROR: " + message);
        }
    }

    /**
     * Forgets every de-duplication token, so previously reported messages can be reported
     * again. Intended for test isolation.
     */
    public static void reset() {
        seen.clear();
    }
}
