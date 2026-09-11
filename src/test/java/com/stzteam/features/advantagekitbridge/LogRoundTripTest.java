package com.stzteam.features.advantagekitbridge;

import static edu.wpi.first.units.Units.Inches;
import static edu.wpi.first.units.Units.Meters;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.littletonrobotics.junction.LogTable;

import com.stzteam.features.advantagekitbridge.fixtures.AllTypesData;
import com.stzteam.features.advantagekitbridge.fixtures.Vec2;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.wpilibj.util.Color;

/**
 * Proves the bridge can write a MARS {@code Data} snapshot into an AdvantageKit
 * {@link LogTable} and read it back byte-identical.
 * <p>
 * This is the test that matters for replay: if any field fails to round-trip, replaying a
 * match diverges from what actually happened, silently. Every supported field shape is
 * therefore written from one object and read into a second object whose values all differ,
 * so a field that is quietly skipped fails the assertion instead of passing by accident.
 */
class LogRoundTripTest {

    @BeforeEach
    void resetBridge() {
        AdvantageKitBridge.reset();
    }

    @Test
    @DisplayName("every supported field shape survives a toLog/fromLog round trip")
    void roundTripsEverySupportedFieldShape() {
        AllTypesData source = new AllTypesData();
        LogTable table = new LogTable(0);
        new MarsLoggableInput(source).toLog(table);

        AllTypesData target = differentFrom();
        new MarsLoggableInput(target).fromLog(table);

        assertEquals(source.flag, target.flag, "boolean");
        assertEquals(source.tinyInt, target.tinyInt, "byte");
        assertEquals(source.smallInt, target.smallInt, "short");
        assertEquals(source.letter, target.letter, "char");
        assertEquals(source.count, target.count, "int");
        assertEquals(source.ticks, target.ticks, "long");
        assertEquals(source.ratio, target.ratio, "float");
        assertEquals(source.position, target.position, "double");

        assertEquals(source.boxedFlag, target.boxedFlag, "Boolean");
        assertEquals(source.boxedCount, target.boxedCount, "Integer");
        assertEquals(source.boxedPosition, target.boxedPosition, "Double");

        assertEquals(source.label, target.label, "String");
        assertEquals(source.mode, target.mode, "enum with a constant body");
        assertArrayEquals(source.modeHistory, target.modeHistory, "enum[]");

        assertArrayEquals(source.packet, target.packet, "byte[]");
        assertArrayEquals(source.flags, target.flags, "boolean[]");
        assertArrayEquals(source.counts, target.counts, "int[]");
        assertArrayEquals(source.timestamps, target.timestamps, "long[]");
        assertArrayEquals(source.ratios, target.ratios, "float[]");
        assertArrayEquals(source.positions, target.positions, "double[]");
        assertArrayEquals(source.labels, target.labels, "String[]");
        assertArrayEquals(source.matrix, target.matrix, "double[][]");
        assertArrayEquals(source.grid, target.grid, "String[][]");

        assertEquals(source.pose, target.pose, "Pose2d");
        assertEquals(source.heading, target.heading, "Rotation2d");
        assertEquals(source.speeds, target.speeds, "ChassisSpeeds");
        assertArrayEquals(source.moduleStates, target.moduleStates, "SwerveModuleState[]");

        assertEquals(source.target, target.target, "ForgeMini-convention struct");
        assertArrayEquals(source.waypoints, target.waypoints, "ForgeMini-convention struct[]");

        assertEquals(source.color, target.color, "Color");
        assertEquals(source.distance, target.distance, "Measure");
        assertEquals(source.renamed, target.renamed, "@LogName field");
        assertEquals(source.timestamp, target.timestamp, "Data.timestamp");
    }

    @Test
    @DisplayName("a struct array is written as an array, not as one oversized struct")
    void structArraysKeepTheirArrayType() {
        AllTypesData source = new AllTypesData();
        LogTable table = new LogTable(0);
        new MarsLoggableInput(source).toLog(table);

        Map<String, LogTable.LogValue> entries = table.getAll(true);
        assertEquals(
                "struct:SwerveModuleState[]",
                entries.get("ModuleStates").customTypeStr,
                "SwerveModuleState[] must be tagged as a struct array");
        assertEquals(
                "struct:SwerveModuleState",
                entries.get("ModuleStates").customTypeStr.replace("[]", ""),
                "sanity check on the scalar type name");
        assertEquals(
                "struct:Vec2[]",
                entries.get("Waypoints").customTypeStr,
                "a ForgeMini-convention struct array must be tagged the same way");
        assertEquals(
                "struct:Pose2d",
                entries.get("Pose").customTypeStr,
                "a scalar struct must not be tagged as an array");
    }

    @Test
    @DisplayName("struct schemas are registered so AdvantageScope can decode the log")
    void structSchemasAreRegistered() {
        LogTable table = new LogTable(0);
        new MarsLoggableInput(new AllTypesData()).toLog(table);

        Map<String, LogTable.LogValue> all = table.getAll(false);
        assertTrue(all.containsKey("/.schema/struct:Pose2d"), "Pose2d schema");
        assertTrue(all.containsKey("/.schema/struct:SwerveModuleState"), "SwerveModuleState schema");
        assertTrue(all.containsKey("/.schema/struct:Vec2"), "team-defined Vec2 schema");
    }

    @Test
    @DisplayName("keys follow the AdvantageKit convention by default")
    void usesAdvantageKitKeyNamingByDefault() {
        LogTable table = new LogTable(0);
        new MarsLoggableInput(new AllTypesData()).toLog(table);

        Map<String, LogTable.LogValue> entries = table.getAll(true);
        assertTrue(entries.containsKey("Position"), "position -> Position");
        assertTrue(entries.containsKey("ModuleStates"), "moduleStates -> ModuleStates");
        assertTrue(entries.containsKey("Timestamp"), "Data.timestamp -> Timestamp");
        assertFalse(entries.containsKey("position"), "the lower-case name must not also appear");
    }

    @Test
    @DisplayName("RAW key style keeps field names exactly as declared")
    void rawKeyStyleKeepsFieldNames() {
        BridgeConfig raw = BridgeConfig.builder().keyStyle(BridgeConfig.KeyStyle.RAW).build();

        LogTable table = new LogTable(0);
        new MarsLoggableInput(new AllTypesData(), raw).toLog(table);

        Map<String, LogTable.LogValue> entries = table.getAll(true);
        assertTrue(entries.containsKey("position"), "position stays lower case");
        assertTrue(entries.containsKey("moduleStates"), "moduleStates stays lower case");
        assertFalse(entries.containsKey("Position"), "the capitalised name must not appear");
    }

    @Test
    @DisplayName("@LogName renames, @LogExclude and transient/static fields disappear")
    void annotationsAndModifiersControlWhatIsLogged() {
        LogTable table = new LogTable(0);
        new MarsLoggableInput(new AllTypesData()).toLog(table);

        Map<String, LogTable.LogValue> entries = table.getAll(true);
        assertTrue(entries.containsKey("CustomKey"), "@LogName key");
        assertFalse(entries.containsKey("Renamed"), "the original name must be gone");
        assertFalse(entries.containsKey("Ignored"), "@LogExclude field");
        assertFalse(entries.containsKey("Scratch"), "transient field");
        assertFalse(entries.containsKey("Shared"), "static field");
    }

    @Test
    @DisplayName("@LogUnit and Measure fields carry unit metadata for AdvantageScope")
    void unitMetadataReachesTheLog() {
        LogTable table = new LogTable(0);
        new MarsLoggableInput(new AllTypesData()).toLog(table);

        Map<String, LogTable.LogValue> entries = table.getAll(true);
        assertEquals("Degrees", entries.get("Position").unitStr, "@LogUnit on a double");
        assertEquals("Meter", entries.get("Distance").unitStr, "base unit name of a Measure");
    }

    @Test
    @DisplayName("a Measure is replayed in its own unit, not the log's base unit")
    void measureKeepsItsUnitOnReplay() {
        AllTypesData source = new AllTypesData();
        source.distance = Meters.of(2.0);

        LogTable table = new LogTable(0);
        new MarsLoggableInput(source).toLog(table);

        AllTypesData target = new AllTypesData();
        target.distance = Inches.of(1.0);
        new MarsLoggableInput(target).fromLog(table);

        assertEquals(2.0, target.distance.in(Meters), 1e-9, "magnitude in base units");
    }

    @Test
    @DisplayName("keys missing from the log leave the current value untouched")
    void absentKeysLeaveCurrentValuesAlone() {
        // An empty table stands in for replaying a log recorded before a field existed.
        LogTable empty = new LogTable(0);

        AllTypesData target = differentFrom();
        double positionBefore = target.position;
        Pose2d poseBefore = target.pose;
        String labelBefore = target.label;

        new MarsLoggableInput(target).fromLog(empty);

        assertEquals(positionBefore, target.position, "double keeps its value");
        assertEquals(poseBefore, target.pose, "struct keeps its value");
        assertEquals(labelBefore, target.label, "String keeps its value");
    }

    @Test
    @DisplayName("a null field is skipped rather than crashing the whole snapshot")
    void nullFieldsAreSkipped() {
        AllTypesData source = new AllTypesData();
        source.label = null;
        source.pose = null;
        source.moduleStates = null;

        LogTable table = new LogTable(0);
        new MarsLoggableInput(source).toLog(table);

        Map<String, LogTable.LogValue> entries = table.getAll(true);
        assertFalse(entries.containsKey("Label"), "null String is not written");
        assertFalse(entries.containsKey("Pose"), "null struct is not written");
        assertTrue(entries.containsKey("Position"), "the rest of the snapshot still logs");
    }

    @Test
    @DisplayName("retargeting an adapter recompiles only when the class changes")
    void adapterRetargetsWithoutRecompiling() {
        MarsLoggableInput adapter = new MarsLoggableInput();
        assertEquals(0, adapter.getFieldCount(), "an untargeted adapter logs nothing");

        adapter.setTarget(new AllTypesData());
        int fieldCount = adapter.getFieldCount();
        assertTrue(fieldCount > 30, "expected the full field set, got " + fieldCount);

        adapter.setTarget(new AllTypesData());
        assertEquals(fieldCount, adapter.getFieldCount(), "same class, same plan");
    }

    /**
     * Builds a snapshot whose every field differs from the defaults, so a field the bridge
     * silently fails to restore cannot pass the round-trip assertions by coincidence.
     */
    private static AllTypesData differentFrom() {
        AllTypesData data = new AllTypesData();
        data.flag = false;
        data.tinyInt = -1;
        data.smallInt = -2;
        data.letter = 'Z';
        data.count = -3;
        data.ticks = -4L;
        data.ratio = -5.0f;
        data.position = -6.0;
        data.boxedFlag = Boolean.TRUE;
        data.boxedCount = -7;
        data.boxedPosition = -8.0;
        data.label = "changed";
        data.mode = AllTypesData.Mode.SLOW;
        data.modeHistory = new AllTypesData.Mode[] {AllTypesData.Mode.FAST};
        data.packet = new byte[] {9};
        data.flags = new boolean[] {false};
        data.counts = new int[] {9};
        data.timestamps = new long[] {9L};
        data.ratios = new float[] {9.0f};
        data.positions = new double[] {9.0};
        data.labels = new String[] {"z"};
        data.matrix = new double[][] {{9.0}};
        data.grid = new String[][] {{"z"}};
        data.pose = new Pose2d(9.0, 9.0, Rotation2d.fromDegrees(9.0));
        data.heading = Rotation2d.fromDegrees(9.0);
        data.speeds = new ChassisSpeeds(9.0, 9.0, 9.0);
        data.moduleStates = new SwerveModuleState[] {
            new SwerveModuleState(9.0, Rotation2d.fromDegrees(9.0))
        };
        data.target = new Vec2(9.0, 9.0);
        data.waypoints = new Vec2[] {new Vec2(9.0, 9.0)};
        data.color = Color.kBlue;
        data.distance = Meters.of(9.0);
        data.renamed = -9.0;
        data.timestamp = -10.0;

        // Guard the guard: if the fixture defaults ever drift into these values, the
        // round-trip test above would stop proving anything.
        assertNotEquals(new AllTypesData().position, data.position);
        return data;
    }
}
