package com.stzteam.features.advantagekitbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stzteam.features.advantagekitbridge.fixtures.TestArmIO;
import com.stzteam.features.advantagekitbridge.internal.LogCodec;
import com.stzteam.features.advantagekitbridge.internal.LogCodecFactory;

/**
 * Checks the bridge against a real MARS {@code Data} subclass rather than a stand-in.
 * <p>
 * The class is only inspected, never instantiated: {@code Data} stamps an FPGA timestamp in
 * a field initialiser, which needs a live HAL. Inspection is enough to prove the two things
 * that could silently break an existing robot -- that inherited framework fields are picked
 * up, and that a {@code Rotation2d} resolves to the struct codec rather than being dropped.
 */
class MarsDataCompatibilityTest {

    @BeforeEach
    void resetBridge() {
        AdvantageKitBridge.reset();
    }

    @Test
    @DisplayName("a real MARS Data subclass exposes exactly its own fields plus the inherited ones")
    void compilesARealMarsDataClass() {
        LogCodec[] codecs =
                LogCodecFactory.forClass(TestArmIO.ArmInputs.class, BridgeConfig.defaults());

        Set<String> keys = Arrays.stream(codecs).map(LogCodec::key).collect(Collectors.toSet());

        assertEquals(
                Set.of("Position", "Rotation", "TargetAngle", "Current", "Timestamp", "Key"),
                keys,
                "the subsystem's own fields plus Data.timestamp and Data.key");
    }

    @Test
    @DisplayName("each field of a real Data class binds to the codec its type deserves")
    void picksTheRightCodecPerField() {
        LogCodec[] codecs =
                LogCodecFactory.forClass(TestArmIO.ArmInputs.class, BridgeConfig.defaults());

        Map<String, String> codecByKey = new HashMap<>();
        for (LogCodec codec : codecs) {
            codecByKey.put(codec.key(), codec.getClass().getSimpleName());
        }

        assertEquals("DoubleCodec", codecByKey.get("Position"), "double");
        assertEquals("DoubleCodec", codecByKey.get("TargetAngle"), "double");
        assertEquals("DoubleCodec", codecByKey.get("Current"), "double");
        assertEquals("DoubleCodec", codecByKey.get("Timestamp"), "inherited Data.timestamp");
        assertEquals("StringCodec", codecByKey.get("Key"), "inherited Data.key");
        assertEquals(
                "StructCodec",
                codecByKey.get("Rotation"),
                "Rotation2d must serialise as a struct, not be skipped");
    }

    @Test
    @DisplayName("compiling the same class twice reuses the cached plan")
    void cachesPerClass() {
        BridgeConfig config = BridgeConfig.defaults();
        LogCodec[] first = LogCodecFactory.forClass(TestArmIO.ArmInputs.class, config);
        LogCodec[] second = LogCodecFactory.forClass(TestArmIO.ArmInputs.class, config);

        assertTrue(first == second, "the compiled plan must be cached, not rebuilt every loop");
    }

    @Test
    @DisplayName("two configurations of the same class get two plans, not one shared by accident")
    void cacheIsScopedToTheConfiguration() {
        BridgeConfig advantageKit = BridgeConfig.defaults();
        BridgeConfig raw = BridgeConfig.builder().keyStyle(BridgeConfig.KeyStyle.RAW).build();

        // Interleaved on purpose: a cache keyed only by class would hand the second call the
        // first call's plan and silently log the wrong keys.
        LogCodec[] first = LogCodecFactory.forClass(TestArmIO.ArmInputs.class, advantageKit);
        LogCodec[] second = LogCodecFactory.forClass(TestArmIO.ArmInputs.class, raw);

        assertTrue(
                Arrays.stream(first).anyMatch(c -> c.key().equals("Position")),
                "the AdvantageKit-style plan capitalises");
        assertTrue(
                Arrays.stream(second).anyMatch(c -> c.key().equals("position")),
                "the raw-style plan does not");
    }

    @Test
    @DisplayName("reconfiguring the bridge drops cached plans so the new key style takes effect")
    void reconfiguringInvalidatesTheCache() {
        LogCodec[] advantageKitStyle =
                LogCodecFactory.forClass(TestArmIO.ArmInputs.class, BridgeConfig.defaults());
        assertTrue(
                Arrays.stream(advantageKitStyle).anyMatch(c -> c.key().equals("Position")),
                "default style capitalises");

        BridgeConfig raw = BridgeConfig.builder().keyStyle(BridgeConfig.KeyStyle.RAW).build();
        AdvantageKitBridge.configure(raw);

        LogCodec[] rawStyle = LogCodecFactory.forClass(TestArmIO.ArmInputs.class, raw);
        assertTrue(
                Arrays.stream(rawStyle).anyMatch(c -> c.key().equals("position")),
                "after reconfiguring, the plan must be rebuilt with the new style");
    }
}
