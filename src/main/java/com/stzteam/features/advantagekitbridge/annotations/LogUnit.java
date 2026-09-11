package com.stzteam.features.advantagekitbridge.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches a unit string to a numeric field so AdvantageScope can label and convert it.
 * <p>
 * AdvantageKit stores the unit as entry metadata (WPILOG) or as a topic property
 * (NetworkTables), exactly like {@code Logger.recordOutput(key, value, unit)} does.
 * Only {@code float}/{@code double} fields (and their boxed forms) carry units; the
 * annotation is ignored elsewhere.
 * <p>
 * This is the runtime-visible counterpart of MARS' source-only {@code @Unit} annotation
 * from the UnitProcessor Feature, which cannot be read reflectively at runtime.
 *
 * <pre>
 * public class ArmInputs extends Data&lt;ArmInputs&gt; {
 *     &#64;LogUnit("Degrees")
 *     public double position = 0.0;
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface LogUnit {

    /**
     * The unit name, e.g. {@code "Degrees"}, {@code "Meters"}, {@code "Volts"}.
     *
     * @return The unit name.
     */
    String value();
}
