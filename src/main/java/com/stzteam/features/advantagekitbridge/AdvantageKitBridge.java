package com.stzteam.features.advantagekitbridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.littletonrobotics.junction.LogFileUtil;
import org.littletonrobotics.junction.Logger;
import org.littletonrobotics.junction.inputs.LoggableInputs;
import org.littletonrobotics.junction.networktables.NT4Publisher;
import org.littletonrobotics.junction.wpilog.WPILOGReader;
import org.littletonrobotics.junction.wpilog.WPILOGWriter;

import com.stzteam.features.advantagekitbridge.generated.FeatureConstants;
import com.stzteam.features.advantagekitbridge.internal.BridgeLog;
import com.stzteam.features.advantagekitbridge.internal.LogCodecFactory;
import com.stzteam.mars.builder.Environment;
import com.stzteam.mars.builder.Environment.RunMode;
import com.stzteam.mars.generated.MarsConstants;
import com.stzteam.mars.models.singlemodule.Data;
import com.stzteam.mars.models.singlemodule.ModularSubsystem;

import edu.wpi.first.wpilibj.RobotBase;

/**
 * The entry point of the MARS &lt;-&gt; AdvantageKit bridge Feature.
 * <p>
 * One call wires every {@link ModularSubsystem} in the robot into AdvantageKit's logging
 * and replay pipeline. No subsystem, {@code IO} interface or {@code Data} class has to
 * change: MARS already hands each input snapshot to its global input hook right after the
 * hardware is polled and before any Request runs, and that is precisely where AdvantageKit
 * needs to sit for replay to be deterministic.
 *
 * <h2>Quick start</h2>
 * The shortest path is {@link MarsLoggedRobot}, which does the whole AdvantageKit setup for
 * you based on the MARS {@link RunMode}:
 *
 * <pre>
 * public class Robot extends MarsLoggedRobot {
 *     public Robot() {
 *         super(Manifest.CURRENT_MODE);
 *         // ... build your RobotContainer as usual
 *     }
 * }
 * </pre>
 *
 * If you would rather drive AdvantageKit yourself, do the standard setup and then install
 * the bridge before {@code Logger.start()}:
 *
 * <pre>
 * AdvantageKitBridge.install();
 * Logger.start();
 * </pre>
 *
 * <h2>What gets logged</h2>
 * <ul>
 *   <li>Every subsystem's {@code Data} snapshot, as AdvantageKit <b>inputs</b>, under the
 *       subsystem's name. These are what replay feeds back in.</li>
 *   <li>MARS diagnostics ({@code ActionStatus} per subsystem, plus the active alert list),
 *       as AdvantageKit <b>outputs</b>. See {@link MarsDiagnosticsLogger}.</li>
 * </ul>
 *
 * <h2>Replay</h2>
 * In {@link RunMode#REPLAY} the hook runs {@code fromLog} instead of {@code toLog},
 * overwriting the snapshot MARS is about to hand to {@code absolutePeriodic}, the active
 * Request and the Telemetry. Everything downstream therefore sees the recorded hardware
 * state rather than live or simulated hardware, which is what makes a replay reproduce the
 * match. This works whether or not a {@code Data} class overrides {@code snapshot()} to
 * return a defensive copy, because MARS passes that same snapshot to the hook.
 *
 * <h2>Threading</h2>
 * Like {@code Logger.processInputs} itself, the bridge must only be driven from the main
 * robot thread.
 */
public final class AdvantageKitBridge {

    // Written from configure()/install() during robot construction and read from the loop
    // thread afterwards; volatile keeps that handoff honest without locking the hot path.
    private static volatile BridgeConfig config = BridgeConfig.defaults();

    private static volatile BiConsumer<String, Data<?>> installedHook;
    private static volatile MarsDiagnosticsLogger diagnostics;

    /** One reusable adapter per subsystem, so the hook never allocates. */
    private static final Map<String, MarsLoggableInput> adapters = new ConcurrentHashMap<>();

    /** Pre-joined "prefix + subsystem name" strings, for the same reason. */
    private static final Map<String, String> tableKeys = new ConcurrentHashMap<>();

    private static long lastDiagnosticsCycle = Long.MIN_VALUE;

    private AdvantageKitBridge() {}

    // ================================================================================
    //  Configuration
    // ================================================================================

    /**
     * Replaces the active configuration. Call this <b>before</b> {@link #install()}: the
     * per-class field plans bake in the key style, so reconfiguring drops every cached plan
     * and any adapter already built.
     *
     * @param newConfig The configuration to use.
     */
    public static synchronized void configure(BridgeConfig newConfig) {
        if (newConfig == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        config = newConfig;
        LogCodecFactory.invalidate();
        adapters.clear();
        tableKeys.clear();
        diagnostics = new MarsDiagnosticsLogger(newConfig);
    }

    /** @return The active configuration, never null. */
    public static BridgeConfig getConfig() {
        return config;
    }

    // ================================================================================
    //  Installation
    // ================================================================================

    /**
     * Registers the bridge with MARS core so every subsystem's inputs flow into
     * AdvantageKit. Idempotent: calling it twice does not double-log.
     * <p>
     * Safe to call before {@code Logger.start()} -- AdvantageKit discards input and output
     * writes until the logger is running.
     */
    public static synchronized void install() {
        if (installedHook != null) {
            return;
        }
        if (diagnostics == null) {
            diagnostics = new MarsDiagnosticsLogger(config);
        }
        lastDiagnosticsCycle = Long.MIN_VALUE;
        installedHook = ModularSubsystem.addGlobalInputHook(AdvantageKitBridge::onInputs);
    }

    /**
     * Removes the bridge from MARS core and drops its per-subsystem caches. Mainly useful
     * for test isolation; a robot program has no reason to uninstall mid-match.
     */
    public static synchronized void uninstall() {
        if (installedHook == null) {
            return;
        }
        ModularSubsystem.removeGlobalInputHook(installedHook);
        installedHook = null;
        adapters.clear();
        tableKeys.clear();
        lastDiagnosticsCycle = Long.MIN_VALUE;
    }

    /** @return True if the bridge is currently forwarding inputs. */
    public static boolean isInstalled() {
        return installedHook != null;
    }

    // ================================================================================
    //  Per-loop work
    // ================================================================================

    private static void onInputs(String name, Data<?> data) {
        if (data == null) {
            return;
        }

        // The first subsystem to report each cycle also flushes diagnostics, so the feature
        // needs nothing added to robotPeriodic() to stay complete. The values published are
        // the statuses as of the end of the previous cycle, since a Request has not run yet
        // this cycle; call recordDiagnostics() yourself if you need them in-cycle.
        if (config.logDiagnostics()) {
            long cycle = Logger.getTimestamp();
            if (cycle != lastDiagnosticsCycle) {
                lastDiagnosticsCycle = cycle;
                diagnostics.record();
            }
        }

        processInputs(name, data);
    }

    /**
     * Logs (or, during replay, restores) one data object under a table of its own. This is
     * what the global hook calls for each subsystem, exposed so you can bring structures
     * MARS does not own -- a vision pipeline's outputs, a co-processor's packet -- into the
     * same replayable pipeline.
     *
     * @param name The table name, to which the configured inputs prefix is prepended.
     * @param data The object to log. Objects already implementing {@link LoggableInputs},
     *             such as classes generated by AdvantageKit's {@code @AutoLog} processor,
     *             are passed straight through and serialise themselves.
     */
    public static void processInputs(String name, Object data) {
        if (data == null) {
            return;
        }
        String key = tableKeys.computeIfAbsent(name, n -> config.inputsPrefix() + n);

        if (data instanceof LoggableInputs autoLogged) {
            Logger.processInputs(key, autoLogged);
            return;
        }

        MarsLoggableInput adapter =
                adapters.computeIfAbsent(name, n -> new MarsLoggableInput(null, config));
        adapter.setTarget(data);
        Logger.processInputs(key, adapter);
    }

    /**
     * Records the MARS diagnostics tree immediately. The bridge already does this once per
     * cycle on its own; call it explicitly only if you want the statuses captured at a
     * specific point of your loop instead.
     */
    public static void recordDiagnostics() {
        if (!config.logDiagnostics()) {
            return;
        }
        if (diagnostics == null) {
            diagnostics = new MarsDiagnosticsLogger(config);
        }
        lastDiagnosticsCycle = Logger.getTimestamp();
        diagnostics.record();
    }

    // ================================================================================
    //  AdvantageKit setup
    // ================================================================================

    /**
     * Performs the standard AdvantageKit receiver/replay-source setup for a MARS
     * {@link RunMode}, then records MARS and Feature versions as log metadata.
     * <p>
     * This is the boilerplate from AdvantageKit's installation guide, expressed once:
     * <ul>
     *   <li>{@link RunMode#REAL}: write a {@code .wpilog} to the USB stick (or to
     *       {@code /home/lvuser/logs}) and publish live to NetworkTables.</li>
     *   <li>{@link RunMode#SIM}: publish live to NetworkTables only.</li>
     *   <li>{@link RunMode#REPLAY}: read the log AdvantageScope selected and write the
     *       recomputed outputs beside it with a {@code _sim} suffix.</li>
     * </ul>
     * It does not call {@code Logger.start()}, so you can add your own receivers first. It
     * also cannot set the replay timing mode, which needs the robot instance -- use
     * {@link MarsLoggedRobot} to get that handled too.
     *
     * @param mode The run mode to configure for.
     */
    public static void configureLogger(RunMode mode) {
        Logger.recordMetadata("MarsVersion", MarsConstants.MARS_VERSION);
        Logger.recordMetadata("MarsFeature", FeatureConstants.FEATURE_NAME);
        Logger.recordMetadata("MarsFeatureVersion", FeatureConstants.FEATURE_VERSION);
        Logger.recordMetadata("MarsRunMode", mode.name());
        Logger.recordMetadata("RobotBase", RobotBase.isReal() ? "roboRIO" : "Desktop");

        switch (mode) {
            case REAL -> {
                Logger.addDataReceiver(new WPILOGWriter());
                Logger.addDataReceiver(new NT4Publisher());
            }
            case SIM -> Logger.addDataReceiver(new NT4Publisher());
            case REPLAY -> {
                String inputPath = LogFileUtil.findReplayLog();
                Logger.setReplaySource(new WPILOGReader(inputPath));
                Logger.addDataReceiver(new WPILOGWriter(LogFileUtil.addPathSuffix(inputPath, "_sim")));
            }
        }
    }

    /**
     * @return True if AdvantageKit is replaying a log rather than reading live hardware.
     *         Prefer this over checking {@link Environment#getMode()} when the question is
     *         "is the logger feeding me recorded inputs right now".
     */
    public static boolean isReplaying() {
        return Logger.hasReplaySource();
    }

    /**
     * Clears every cached field plan, adapter and warning. Intended for tests that compile
     * the same classes repeatedly under different configurations.
     */
    public static synchronized void reset() {
        uninstall();
        LogCodecFactory.invalidate();
        BridgeLog.reset();
        config = BridgeConfig.defaults();
        diagnostics = null;
    }
}
