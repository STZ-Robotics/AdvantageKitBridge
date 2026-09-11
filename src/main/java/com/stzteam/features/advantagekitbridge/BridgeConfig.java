package com.stzteam.features.advantagekitbridge;

/**
 * Immutable configuration for the MARS &lt;-&gt; AdvantageKit bridge.
 * <p>
 * Build one with {@link #builder()} and hand it to
 * {@link AdvantageKitBridge#configure(BridgeConfig)} <b>before</b> calling
 * {@link AdvantageKitBridge#install()}; the field codecs the bridge compiles for each
 * {@code Data} class depend on these settings, so changing them afterwards would leave
 * already-compiled subsystems on the old naming scheme.
 *
 * <pre>
 * AdvantageKitBridge.configure(
 *     BridgeConfig.builder()
 *         .keyStyle(BridgeConfig.KeyStyle.ADVANTAGEKIT)
 *         .inputsPrefix("")
 *         .logDiagnostics(true)
 *         .build());
 * </pre>
 */
public final class BridgeConfig {

    /** How a {@code Data} field name is turned into an AdvantageKit log key. */
    public enum KeyStyle {
        /**
         * AdvantageKit's own convention, matching what the {@code @AutoLog} annotation
         * processor generates: the first character is upper-cased, the rest is untouched.
         * {@code position} becomes {@code Position}, {@code velocityRPS} becomes
         * {@code VelocityRPS}.
         */
        ADVANTAGEKIT,

        /**
         * The field name exactly as declared, matching what ForgeMini's {@code NetworkIO}
         * publishes to NetworkTables. Use this if you want the AdvantageKit tree and the
         * ForgeMini tree to line up key-for-key.
         */
        RAW
    }

    private final KeyStyle keyStyle;
    private final String inputsPrefix;
    private final boolean logDiagnostics;
    private final String diagnosticsRoot;
    private final boolean logStatusColor;
    private final boolean logNestedObjects;
    private final int maxNestingDepth;

    private BridgeConfig(Builder builder) {
        this.keyStyle = builder.keyStyle;
        this.inputsPrefix = builder.inputsPrefix;
        this.logDiagnostics = builder.logDiagnostics;
        this.diagnosticsRoot = builder.diagnosticsRoot;
        this.logStatusColor = builder.logStatusColor;
        this.logNestedObjects = builder.logNestedObjects;
        this.maxNestingDepth = builder.maxNestingDepth;
    }

    /**
     * @return The default configuration: AdvantageKit key style, no prefix, diagnostics on.
     */
    public static BridgeConfig defaults() {
        return builder().build();
    }

    /**
     * @return A new builder pre-loaded with the default values.
     */
    public static Builder builder() {
        return new Builder();
    }

    /** @return The configured field-name to log-key conversion. */
    public KeyStyle keyStyle() {
        return keyStyle;
    }

    /**
     * @return A prefix prepended to every subsystem input table, e.g. {@code Mars/}. Empty
     *         by default, which puts inputs at the root of the log exactly where a
     *         hand-written AdvantageKit project would put them.
     */
    public String inputsPrefix() {
        return inputsPrefix;
    }

    /** @return Whether MARS {@code ActionStatus} diagnostics are recorded as outputs. */
    public boolean logDiagnostics() {
        return logDiagnostics;
    }

    /** @return The output key the diagnostics tree is written under. */
    public String diagnosticsRoot() {
        return diagnosticsRoot;
    }

    /**
     * @return Whether each status' evaluated LED hex colour is recorded. This changes every
     *         loop for blinking patterns, so it costs a string per subsystem per cycle.
     */
    public boolean logStatusColor() {
        return logStatusColor;
    }

    /**
     * @return Whether fields holding a plain object (no struct, no record, not a primitive)
     *         are recursed into as a nested subtable. Off by default: a stray reference to a
     *         motor controller or a subsystem would otherwise drag its whole object graph
     *         into the log.
     */
    public boolean logNestedObjects() {
        return logNestedObjects;
    }

    /** @return How deep {@link #logNestedObjects()} is allowed to recurse. */
    public int maxNestingDepth() {
        return maxNestingDepth;
    }

    /** Fluent builder for {@link BridgeConfig}. */
    public static final class Builder {

        private KeyStyle keyStyle = KeyStyle.ADVANTAGEKIT;
        private String inputsPrefix = "";
        private boolean logDiagnostics = true;
        private String diagnosticsRoot = "Mars/Diagnostics";
        private boolean logStatusColor = true;
        private boolean logNestedObjects = false;
        private int maxNestingDepth = 3;

        private Builder() {}

        /**
         * Sets how field names become log keys.
         *
         * @param keyStyle The naming convention to use.
         * @return This builder.
         */
        public Builder keyStyle(KeyStyle keyStyle) {
            this.keyStyle = require(keyStyle, "keyStyle");
            return this;
        }

        /**
         * Sets a prefix for every subsystem input table. A trailing slash is added if
         * missing, so {@code Mars} and {@code Mars/} behave identically.
         *
         * @param inputsPrefix The prefix, or an empty string for none.
         * @return This builder.
         */
        public Builder inputsPrefix(String inputsPrefix) {
            String value = require(inputsPrefix, "inputsPrefix").trim();
            while (value.startsWith("/")) {
                value = value.substring(1);
            }
            if (!value.isEmpty() && !value.endsWith("/")) {
                value = value + "/";
            }
            this.inputsPrefix = value;
            return this;
        }

        /**
         * Enables or disables recording MARS diagnostics (per-subsystem {@code ActionStatus}
         * and the active alert list) as AdvantageKit outputs.
         *
         * @param logDiagnostics True to record diagnostics.
         * @return This builder.
         */
        public Builder logDiagnostics(boolean logDiagnostics) {
            this.logDiagnostics = logDiagnostics;
            return this;
        }

        /**
         * Sets the output key the diagnostics tree is written under.
         *
         * @param diagnosticsRoot The root key, e.g. {@code Mars/Diagnostics}.
         * @return This builder.
         */
        public Builder diagnosticsRoot(String diagnosticsRoot) {
            String value = require(diagnosticsRoot, "diagnosticsRoot").trim();
            while (value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException("diagnosticsRoot must not be empty");
            }
            this.diagnosticsRoot = value;
            return this;
        }

        /**
         * Enables or disables recording the evaluated LED hex colour of each status.
         *
         * @param logStatusColor True to record colours.
         * @return This builder.
         */
        public Builder logStatusColor(boolean logStatusColor) {
            this.logStatusColor = logStatusColor;
            return this;
        }

        /**
         * Enables recursing into plain-object fields as nested subtables.
         *
         * @param logNestedObjects True to recurse into unrecognised object fields.
         * @return This builder.
         */
        public Builder logNestedObjects(boolean logNestedObjects) {
            this.logNestedObjects = logNestedObjects;
            return this;
        }

        /**
         * Sets the recursion limit used by {@link #logNestedObjects(boolean)}.
         *
         * @param maxNestingDepth Maximum depth, at least 1.
         * @return This builder.
         */
        public Builder maxNestingDepth(int maxNestingDepth) {
            if (maxNestingDepth < 1) {
                throw new IllegalArgumentException("maxNestingDepth must be at least 1");
            }
            this.maxNestingDepth = maxNestingDepth;
            return this;
        }

        /** @return The finished, immutable configuration. */
        public BridgeConfig build() {
            return new BridgeConfig(this);
        }

        private static <T> T require(T value, String name) {
            if (value == null) {
                throw new IllegalArgumentException(name + " must not be null");
            }
            return value;
        }
    }
}
