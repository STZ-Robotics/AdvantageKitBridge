package com.stzteam.features.advantagekitbridge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.littletonrobotics.junction.LogTable;

import com.stzteam.features.advantagekitbridge.fixtures.AwkwardData;

/**
 * Checks how the bridge behaves at the edges of what AdvantageKit can represent.
 * <p>
 * The rule being enforced is that an unloggable field costs you that field and nothing else.
 * Failing loudly here would mean a subsystem's whole snapshot disappears -- or worse, that
 * {@code periodic} throws -- because someone put a motor controller reference in a
 * {@code Data} class.
 */
class UnsupportedFieldTest {

    @BeforeEach
    void resetBridge() {
        AdvantageKitBridge.reset();
    }

    @Test
    @DisplayName("an unloggable field is skipped without taking the snapshot down with it")
    void unsupportedFieldsAreSkipped() {
        LogTable table = new LogTable(0);
        AwkwardData data = new AwkwardData();

        assertDoesNotThrow(() -> new MarsLoggableInput(data).toLog(table));

        Map<String, LogTable.LogValue> entries = table.getAll(true);
        assertTrue(entries.containsKey("Good"), "the loggable field still reaches the log");
        assertFalse(entries.containsKey("UnsupportedArray"), "short[] has no AdvantageKit type");
        assertFalse(entries.containsKey("Opaque"), "an opaque reference is skipped");
        assertFalse(entries.containsKey("Nested"), "nesting is off by default");
    }

    @Test
    @DisplayName("replaying a snapshot with unloggable fields is equally harmless")
    void unsupportedFieldsDoNotBreakReplay() {
        LogTable table = new LogTable(0);
        AwkwardData source = new AwkwardData();
        source.good = 7.5;
        new MarsLoggableInput(source).toLog(table);

        AwkwardData target = new AwkwardData();
        target.good = -1.0;
        assertDoesNotThrow(() -> new MarsLoggableInput(target).fromLog(table));

        assertEquals(7.5, target.good, "the loggable field is restored");
        assertEquals(4.5, target.nested.inner, "a skipped field keeps whatever it had");
    }

    @Test
    @DisplayName("enabling nesting recurses into plain objects as their own subtable")
    void nestedObjectsAreLoggedWhenEnabled() {
        BridgeConfig nesting = BridgeConfig.builder().logNestedObjects(true).build();

        LogTable table = new LogTable(0);
        AwkwardData source = new AwkwardData();
        source.nested.inner = 9.5;
        source.nested.name = "changed";
        new MarsLoggableInput(source, nesting).toLog(table);

        Map<String, LogTable.LogValue> entries = table.getAll(true);
        assertTrue(entries.containsKey("Nested/Inner"), "nested double");
        assertTrue(entries.containsKey("Nested/Name"), "nested string");
        assertFalse(
                entries.containsKey("Opaque/Payload"),
                "an object with nothing loggable inside stays skipped");

        AwkwardData target = new AwkwardData();
        new MarsLoggableInput(target, nesting).fromLog(table);
        assertEquals(9.5, target.nested.inner, "nested fields replay too");
        assertEquals("changed", target.nested.name);
    }
}
