package com.stzteam.features.advantagekitbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Checks the configuration surface, including the input normalisation users rely on. */
class BridgeConfigTest {

    @AfterEach
    void resetBridge() {
        AdvantageKitBridge.reset();
    }

    @Test
    @DisplayName("defaults match a hand-written AdvantageKit project")
    void defaultsMatchPlainAdvantageKit() {
        BridgeConfig config = BridgeConfig.defaults();

        assertEquals(BridgeConfig.KeyStyle.ADVANTAGEKIT, config.keyStyle());
        assertEquals("", config.inputsPrefix(), "inputs land at the root of the log");
        assertTrue(config.logDiagnostics());
        assertEquals("Mars/Diagnostics", config.diagnosticsRoot());
        assertFalse(config.logNestedObjects(), "following arbitrary references must be opt-in");
    }

    @Test
    @DisplayName("an inputs prefix is normalised so Mars and /Mars/ mean the same thing")
    void inputsPrefixIsNormalised() {
        assertEquals("Mars/", BridgeConfig.builder().inputsPrefix("Mars").build().inputsPrefix());
        assertEquals("Mars/", BridgeConfig.builder().inputsPrefix("Mars/").build().inputsPrefix());
        assertEquals("Mars/", BridgeConfig.builder().inputsPrefix("/Mars").build().inputsPrefix());
        assertEquals("Mars/", BridgeConfig.builder().inputsPrefix("  Mars  ").build().inputsPrefix());
        assertEquals("", BridgeConfig.builder().inputsPrefix("").build().inputsPrefix());
    }

    @Test
    @DisplayName("a diagnostics root loses its trailing slash but may not be empty")
    void diagnosticsRootIsNormalised() {
        assertEquals(
                "Robot/Health",
                BridgeConfig.builder().diagnosticsRoot("Robot/Health/").build().diagnosticsRoot());
        assertThrows(
                IllegalArgumentException.class,
                () -> BridgeConfig.builder().diagnosticsRoot("/").build());
    }

    @Test
    @DisplayName("invalid configuration is rejected at build time, not mid-match")
    void rejectsInvalidValues() {
        assertThrows(
                IllegalArgumentException.class, () -> BridgeConfig.builder().keyStyle(null).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> BridgeConfig.builder().inputsPrefix(null).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> BridgeConfig.builder().maxNestingDepth(0).build());
    }

    @Test
    @DisplayName("configure() installs the configuration the adapters then pick up")
    void configureReplacesTheActiveConfig() {
        BridgeConfig custom = BridgeConfig.builder().inputsPrefix("Mars").build();
        AdvantageKitBridge.configure(custom);

        assertSame(custom, AdvantageKitBridge.getConfig());
        assertThrows(IllegalArgumentException.class, () -> AdvantageKitBridge.configure(null));
    }

    @Test
    @DisplayName("reset() puts the bridge back to a clean, uninstalled state")
    void resetRestoresDefaults() {
        AdvantageKitBridge.configure(BridgeConfig.builder().inputsPrefix("Mars").build());
        AdvantageKitBridge.reset();

        assertEquals("", AdvantageKitBridge.getConfig().inputsPrefix());
        assertFalse(AdvantageKitBridge.isInstalled());
    }
}
