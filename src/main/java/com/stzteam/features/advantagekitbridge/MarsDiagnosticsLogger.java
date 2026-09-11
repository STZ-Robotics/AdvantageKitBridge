package com.stzteam.features.advantagekitbridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.littletonrobotics.junction.Logger;

import com.stzteam.mars.diagnostics.ActionStatus;
import com.stzteam.mars.diagnostics.AlertRegistry;
import com.stzteam.mars.diagnostics.DiagnosticPayload;
import com.stzteam.mars.diagnostics.StatusColorCode;

/**
 * Mirrors MARS' diagnostic state into AdvantageKit outputs.
 * <p>
 * Every MARS subsystem reports an {@code ActionStatus} each loop, and
 * {@link AlertRegistry} already collects the latest one per subsystem. That registry is a
 * global, so the bridge can publish the whole robot's health without being handed a single
 * subsystem reference -- which is what keeps installation to one line.
 * <p>
 * These are recorded as <b>outputs</b>, not inputs, because a status is derived from the
 * inputs by the subsystem's Request. During replay they are recomputed from the replayed
 * inputs, so comparing {@code /RealOutputs} against {@code /ReplayOutputs} in
 * AdvantageScope shows exactly where a logic change altered the robot's decisions.
 *
 * <h2>Log layout</h2>
 * <pre>
 * Mars/Diagnostics/&lt;Subsystem&gt;/Code        (string)  e.g. "NOMINAL"
 * Mars/Diagnostics/&lt;Subsystem&gt;/Severity    (string)  OK | WARNING | ERROR | CRITICAL
 * Mars/Diagnostics/&lt;Subsystem&gt;/Message     (string)
 * Mars/Diagnostics/&lt;Subsystem&gt;/Timestamp   (double)  FPGA seconds
 * Mars/Diagnostics/&lt;Subsystem&gt;/ColorHex    (string)  evaluated LED colour
 * Mars/Diagnostics/ActiveAlerts             (string[]) non-nominal subsystems
 * Mars/Diagnostics/ActiveAlertCount         (int)
 * Mars/Diagnostics/HasCriticalAlert         (boolean)
 * </pre>
 */
public final class MarsDiagnosticsLogger {

    private final BridgeConfig config;

    /** Key strings are stable per subsystem, so they are built once instead of per loop. */
    private final Map<String, SubsystemKeys> keyCache = new HashMap<>();

    private final String activeAlertsKey;
    private final String activeAlertCountKey;
    private final String hasCriticalAlertKey;

    private final List<String> alertScratch = new ArrayList<>();

    /**
     * @param config The bridge configuration supplying the diagnostics root key.
     */
    public MarsDiagnosticsLogger(BridgeConfig config) {
        this.config = config;
        String root = config.diagnosticsRoot();
        this.activeAlertsKey = root + "/ActiveAlerts";
        this.activeAlertCountKey = root + "/ActiveAlertCount";
        this.hasCriticalAlertKey = root + "/HasCriticalAlert";
    }

    /**
     * Records the current diagnostic state of every reporting subsystem. Safe to call when
     * the logger is not running; AdvantageKit ignores output writes until then.
     */
    public void record() {
        Map<String, ActionStatus> statuses = AlertRegistry.getInstance().getAllStatuses();

        alertScratch.clear();
        boolean hasCritical = false;

        for (Map.Entry<String, ActionStatus> entry : statuses.entrySet()) {
            String name = entry.getKey();
            ActionStatus status = entry.getValue();
            if (status == null || status.code == null) {
                continue;
            }

            SubsystemKeys keys = keyCache.computeIfAbsent(
                    name, n -> new SubsystemKeys(config.diagnosticsRoot(), n));

            StatusColorCode.Severity severity = status.code.getSeverity();
            Logger.recordOutput(keys.code, status.code.getName());
            Logger.recordOutput(keys.severity, severity);
            Logger.recordOutput(keys.message, status.message == null ? "" : status.message);
            Logger.recordOutput(keys.timestamp, status.timestamp);

            if (config.logStatusColor()) {
                DiagnosticPayload payload = status.getPayload();
                Logger.recordOutput(keys.colorHex, payload.colorHex());
            }

            if (severity != StatusColorCode.Severity.OK) {
                alertScratch.add(name + ": " + status.message);
                hasCritical |= severity == StatusColorCode.Severity.CRITICAL;
            }
        }

        // Sorted so the array is stable cycle to cycle; the registry is a concurrent map and
        // does not promise an iteration order.
        alertScratch.sort(String::compareTo);

        Logger.recordOutput(activeAlertsKey, alertScratch.toArray(new String[0]));
        Logger.recordOutput(activeAlertCountKey, alertScratch.size());
        Logger.recordOutput(hasCriticalAlertKey, hasCritical);
    }

    /** Pre-built key strings for one subsystem's diagnostic entries. */
    private static final class SubsystemKeys {
        private final String code;
        private final String severity;
        private final String message;
        private final String timestamp;
        private final String colorHex;

        private SubsystemKeys(String root, String subsystem) {
            String base = root + "/" + subsystem + "/";
            this.code = base + "Code";
            this.severity = base + "Severity";
            this.message = base + "Message";
            this.timestamp = base + "Timestamp";
            this.colorHex = base + "ColorHex";
        }
    }
}
