package com.stzteam.features.advantagekitbridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.littletonrobotics.junction.LogTable;

/**
 * Round-trips record fields.
 * <p>
 * Disabled unless {@code -DmarsBridge.halTests=true} is passed. AdvantageKit derives a
 * record's struct layout lazily, and while doing so asks the Driver Station whether the robot
 * is enabled in order to warn about the cost. That pulls in the HAL, which cannot be loaded
 * on every development machine -- and when it fails to load, it takes the JVM with it rather
 * than throwing something a test could catch. Records work normally on a robot, in simulation
 * and in replay, where the HAL is always present.
 *
 * <pre>
 * ./gradlew test -DmarsBridge.halTests=true
 * </pre>
 */
@EnabledIfSystemProperty(
        named = "marsBridge.halTests",
        matches = "true",
        disabledReason = "record serialization in AdvantageKit requires a loadable HAL")
class RecordLoggingTest {

    /** A record standing in for a team's setpoint or measurement struct. */
    public record Setpoint(double position, double velocity) {}

    /** A snapshot carrying record fields. */
    public static class RecordData {
        /** Single record. */
        public Setpoint setpoint = new Setpoint(1.0, 2.0);
        /** Array of records. */
        public Setpoint[] trajectory = {new Setpoint(0.0, 0.0), new Setpoint(1.0, 1.0)};
    }

    @BeforeEach
    void resetBridge() {
        AdvantageKitBridge.reset();
    }

    @Test
    @DisplayName("record fields survive a round trip")
    void recordsRoundTrip() {
        RecordData source = new RecordData();
        LogTable table = new LogTable(0);
        new MarsLoggableInput(source).toLog(table);

        RecordData target = new RecordData();
        target.setpoint = new Setpoint(9.0, 9.0);
        target.trajectory = new Setpoint[] {new Setpoint(9.0, 9.0)};
        new MarsLoggableInput(target).fromLog(table);

        assertEquals(source.setpoint, target.setpoint, "record");
        assertArrayEquals(source.trajectory, target.trajectory, "record[]");
    }

    @Test
    @DisplayName("a record array is tagged as an array, not as one oversized struct")
    void recordArraysKeepTheirArrayType() {
        LogTable table = new LogTable(0);
        new MarsLoggableInput(new RecordData()).toLog(table);

        var entries = table.getAll(true);
        assertEquals("struct:Setpoint", entries.get("Setpoint").customTypeStr);
        assertEquals("struct:Setpoint[]", entries.get("Trajectory").customTypeStr);
    }
}
