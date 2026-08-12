package com.stzteam.features.advantagekitbridge;

import java.util.function.BiConsumer;

import org.littletonrobotics.junction.Logger;

import com.stzteam.mars.models.singlemodule.Data;
import com.stzteam.mars.models.singlemodule.ModularSubsystem;

/**
 * Entry point for the MARS &lt;-&gt; AdvantageKit bridge Feature.
 * <p>
 * Call {@link #install()} once during {@code robotInit()}, after {@code Logger.start()},
 * to make every {@link ModularSubsystem}'s input snapshots automatically visible to
 * AdvantageKit's logging and replay pipeline — no changes required to existing
 * subsystems or Data classes.
 * <p>
 * Requires the robot's main class to extend {@code LoggedRobot} instead of
 * {@code TimedRobot}, per standard AdvantageKit setup.
 */
public final class MarsAdvantageKitBridge {

    private static BiConsumer<String, Data<?>> installedHook = null;

    private MarsAdvantageKitBridge() {}

    /**
     * Registers the bridge's input hook with MARS core. Safe to call multiple times;
     * subsequent calls are no-ops if already installed.
     */
    public static synchronized void install() {
        if (installedHook != null) return;

        installedHook = ModularSubsystem.addGlobalInputHook((name, data) ->
            Logger.processInputs(name, new MarsLoggableInput(data))
        );
    }

    /**
     * Removes the bridge's input hook from MARS core. Mainly useful for test isolation.
     */
    public static synchronized void uninstall() {
        if (installedHook != null) {
            ModularSubsystem.removeGlobalInputHook(installedHook);
            installedHook = null;
        }
    }

    /**
     * @return true if the bridge is currently installed and actively forwarding inputs.
     */
    public static boolean isInstalled() {
        return installedHook != null;
    }
}