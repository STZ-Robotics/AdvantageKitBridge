package com.stzteam.features.advantagekitbridge.fixtures;

import com.stzteam.mars.models.singlemodule.Data;
import com.stzteam.mars.models.singlemodule.IO;

import edu.wpi.first.math.geometry.Rotation2d;

/**
 * A MARS IO interface written exactly the way a real subsystem writes one, copied in shape
 * from the arm module of the MARS base robot.
 * <p>
 * Used to prove the bridge understands a genuine {@code Data} subclass -- inherited
 * {@code timestamp} and {@code key} included -- without needing to instantiate it, which
 * would require a live HAL for the FPGA timestamp in {@code Data}'s field initialiser.
 */
public interface TestArmIO extends IO<TestArmIO.ArmInputs> {

    /** The arm's hardware snapshot. */
    class ArmInputs extends Data<ArmInputs> {
        /** Arm position in degrees. */
        public double position = 0.0;
        /** Arm rotation, a struct-serialisable type. */
        public Rotation2d rotation = new Rotation2d();
        /** Commanded angle in degrees. */
        public double targetAngle = 0.0;
        /** Stator current in amps. */
        public double current = 0.0;
    }

    /**
     * Drives the arm with an open-loop voltage.
     *
     * @param volts The voltage to apply.
     */
    void applyOutput(double volts);

    /**
     * @return The last commanded voltage, used here to check that a replayed IO returns a
     *         zero value rather than throwing.
     */
    double getLastVolts();

    /**
     * @return A description, used here to check that {@code default} methods keep their real
     *         implementation when the IO is replaced by a replay proxy.
     */
    default String describe() {
        return "arm";
    }
}
