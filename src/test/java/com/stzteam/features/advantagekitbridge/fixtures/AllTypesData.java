package com.stzteam.features.advantagekitbridge.fixtures;

import static edu.wpi.first.units.Units.Meters;

import com.stzteam.features.advantagekitbridge.annotations.LogExclude;
import com.stzteam.features.advantagekitbridge.annotations.LogName;
import com.stzteam.features.advantagekitbridge.annotations.LogUnit;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.units.measure.Distance;
import edu.wpi.first.wpilibj.util.Color;

/**
 * A snapshot class exercising every field shape the bridge claims to support.
 * <p>
 * Written the way a real MARS subsystem writes one -- plain public fields, no annotations
 * required -- so a green round-trip here is evidence that existing MARS subsystems work
 * untouched.
 * <p>
 * It deliberately mirrors {@code com.stzteam.mars.models.singlemodule.Data} rather than
 * extending it, including its {@code timestamp} and {@code key} fields. {@code Data} stamps
 * an FPGA timestamp in a field initialiser, which needs a live HAL, and the desktop HAL
 * cannot be loaded on every developer machine. Real {@code Data} subclasses are covered
 * without instantiation by {@code MarsDataCompatibilityTest}.
 */
public class AllTypesData {

    // --- the two fields every MARS Data subclass inherits ---
    /** Mirrors {@code Data.timestamp}. */
    public double timestamp = 12.5;
    /** Mirrors {@code Data.key}, which is normally left null. */
    public String key;

    /** Example enum field, including one constant with a body to stress enum resolution. */
    public enum Mode {
        /** Plain constant. */
        SLOW,
        /** Constant with a body, which is an anonymous subclass at runtime. */
        FAST {
            @Override
            public String describe() {
                return "fast";
            }
        };

        /** @return A description of this mode. */
        public String describe() {
            return "slow";
        }
    }

    // --- primitives ---
    /** Primitive boolean. */
    public boolean flag = true;
    /** Primitive byte. */
    public byte tinyInt = 7;
    /** Primitive short. */
    public short smallInt = 300;
    /** Primitive char. */
    public char letter = 'M';
    /** Primitive int. */
    public int count = 42;
    /** Primitive long. */
    public long ticks = 1234567890123L;
    /** Primitive float. */
    public float ratio = 1.5f;
    /** Primitive double carrying a unit for AdvantageScope. */
    @LogUnit("Degrees")
    public double position = 3.25;

    // --- boxed scalars ---
    /** Boxed boolean. */
    public Boolean boxedFlag = Boolean.FALSE;
    /** Boxed integer. */
    public Integer boxedCount = 9;
    /** Boxed double. */
    public Double boxedPosition = 2.5;

    // --- text and enums ---
    /** String field. */
    public String label = "hello";
    /** Enum field. */
    public Mode mode = Mode.FAST;
    /** Enum array field. */
    public Mode[] modeHistory = {Mode.SLOW, Mode.FAST};

    // --- arrays ---
    /** Raw byte array. */
    public byte[] packet = {1, 2, 3};
    /** Boolean array. */
    public boolean[] flags = {true, false, true};
    /** Int array. */
    public int[] counts = {1, 2, 3};
    /** Long array. */
    public long[] timestamps = {10L, 20L};
    /** Float array. */
    public float[] ratios = {0.5f, 1.5f};
    /** Double array. */
    public double[] positions = {1.0, 2.0, 3.0};
    /** String array. */
    public String[] labels = {"a", "b"};
    /** Two-dimensional double array. */
    public double[][] matrix = {{1.0, 2.0}, {3.0, 4.0}};
    /** Two-dimensional string array. */
    public String[][] grid = {{"a"}, {"b", "c"}};

    // --- WPILib serializable types ---
    /** Struct-serialisable pose. */
    public Pose2d pose = new Pose2d(1.0, 2.0, Rotation2d.fromDegrees(30.0));
    /** Struct-serialisable rotation. */
    public Rotation2d heading = Rotation2d.fromDegrees(45.0);
    /** Struct-serialisable chassis speeds. */
    public ChassisSpeeds speeds = new ChassisSpeeds(1.0, 2.0, 3.0);
    /** Struct-serialisable array. */
    public SwerveModuleState[] moduleStates = {
        new SwerveModuleState(1.0, Rotation2d.fromDegrees(10.0)),
        new SwerveModuleState(2.0, Rotation2d.fromDegrees(20.0))
    };

    /** A type following ForgeMini's convention: a static struct, but no marker interface. */
    public Vec2 target = new Vec2(4.0, 5.0);
    /** An array of the same. */
    public Vec2[] waypoints = {new Vec2(1.0, 1.0), new Vec2(2.0, 2.0)};

    // --- other supported shapes ---
    /** Colour field, stored as a hex string. */
    public Color color = Color.kRed;
    /** Unit-carrying measure. */
    public Distance distance = Meters.of(5.0);

    // --- annotation behaviour ---
    /** Renamed field, to prove {@code @LogName} wins over the naming style. */
    @LogName("CustomKey")
    public double renamed = 8.5;
    /** Skipped field, to prove {@code @LogExclude} removes it from the log entirely. */
    @LogExclude
    public double ignored = 99.0;
    /** Transient field, which must be skipped the same way. */
    public transient double scratch = 77.0;
    /** Static field, which must never be logged. */
    public static double shared = 55.0;
}
