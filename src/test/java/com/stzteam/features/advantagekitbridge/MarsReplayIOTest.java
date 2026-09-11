package com.stzteam.features.advantagekitbridge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stzteam.features.advantagekitbridge.fixtures.TestArmIO;
import com.stzteam.mars.builder.Environment;
import com.stzteam.mars.builder.Environment.RunMode;

/**
 * Checks the generated replay IO behaves the way MARS needs it to.
 * <p>
 * The subtle requirement is {@code isFallback()}: MARS skips the entire periodic block of a
 * fallback subsystem, and a skipped block never reaches the bridge's hook. A replay IO that
 * reported itself as a fallback would therefore produce an empty replay, quietly.
 */
class MarsReplayIOTest {

    @AfterEach
    void restoreMode() {
        Environment.setMode(RunMode.REAL);
    }

    @Test
    @DisplayName("a replayed IO never reports itself as a fallback")
    void neverReportsFallback() {
        TestArmIO io = MarsReplayIO.of(TestArmIO.class);
        assertFalse(io.isFallback(), "a fallback IO would make MARS skip the subsystem entirely");
    }

    @Test
    @DisplayName("a replayed IO leaves the snapshot alone for the log to fill")
    void updateInputsDoesNothing() {
        TestArmIO io = MarsReplayIO.of(TestArmIO.class);

        // A null snapshot is the strongest form of "did not touch it": a real IO layer would
        // dereference it immediately. Constructing a real ArmInputs is not an option here,
        // since MARS' Data stamps an FPGA timestamp in a field initialiser.
        assertDoesNotThrow(() -> io.updateInputs(null), "updateInputs must be a pure no-op");
    }

    @Test
    @DisplayName("actuator calls are inert and return zero values")
    void actuatorCallsAreInert() {
        TestArmIO io = MarsReplayIO.of(TestArmIO.class);

        io.applyOutput(9.0);
        assertEquals(0.0, io.getLastVolts(), "abstract getters return a zero value");
    }

    @Test
    @DisplayName("default methods on the IO interface keep their real implementation")
    void defaultMethodsStillRun() {
        TestArmIO io = MarsReplayIO.of(TestArmIO.class);
        assertEquals("arm", io.describe(), "default methods must not be stubbed out");
    }

    @Test
    @DisplayName("Object methods answer sensibly instead of falling through")
    void objectMethodsWork() {
        TestArmIO io = MarsReplayIO.of(TestArmIO.class);

        assertTrue(io.equals(io), "identity equality");
        assertFalse(io.equals(MarsReplayIO.of(TestArmIO.class)), "distinct proxies differ");
        assertNotNull(io.toString());
        assertEquals(io.hashCode(), io.hashCode(), "hashCode is stable");
    }

    @Test
    @DisplayName("createIO only substitutes the replay proxy in REPLAY mode")
    void createIoRespectsTheRunMode() {
        Environment.setMode(RunMode.SIM);
        TestArmIO sim = MarsReplayIO.createIO(
                TestArmIO.class, true, StubArmIO::new, StubArmIO::new, StubArmIO::new);
        assertTrue(sim instanceof StubArmIO, "SIM must still get the simulation implementation");

        Environment.setMode(RunMode.REPLAY);
        TestArmIO replay = MarsReplayIO.createIO(
                TestArmIO.class, true, StubArmIO::new, StubArmIO::new, StubArmIO::new);
        assertFalse(replay instanceof StubArmIO, "REPLAY must get the inert proxy");
        assertFalse(replay.isFallback());
    }

    @Test
    @DisplayName("a disabled module still gets its fallback, even in REPLAY")
    void disabledModulesKeepTheirFallback() {
        Environment.setMode(RunMode.REPLAY);
        TestArmIO io = MarsReplayIO.createIO(
                TestArmIO.class, false, StubArmIO::new, StubArmIO::new, StubArmIO::new);

        assertTrue(io instanceof StubArmIO, "a module that does not exist must stay a fallback");
    }

    @Test
    @DisplayName("asking for a replay IO of a class rather than an interface is rejected loudly")
    void rejectsNonInterfaces() {
        assertThrows(IllegalArgumentException.class, () -> MarsReplayIO.of(StubArmIO.class));
    }

    /** A plain implementation standing in for a real or simulated IO layer. */
    static final class StubArmIO implements TestArmIO {
        private double volts;

        @Override
        public void updateInputs(ArmInputs inputs) {
            inputs.position = 99.0;
        }

        @Override
        public void applyOutput(double volts) {
            this.volts = volts;
        }

        @Override
        public double getLastVolts() {
            return volts;
        }

        @Override
        public boolean isFallback() {
            return true;
        }
    }
}
