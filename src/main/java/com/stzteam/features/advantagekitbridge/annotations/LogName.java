package com.stzteam.features.advantagekitbridge.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the AdvantageKit log key used for a field of a MARS {@code Data} class.
 * <p>
 * By default the bridge derives the key from the field name (see
 * {@link com.stzteam.features.advantagekitbridge.BridgeConfig.KeyStyle}). Use this
 * annotation when a field name and its desired log key must differ, for example to keep
 * an existing AdvantageScope layout working after a refactor.
 *
 * <pre>
 * public class ArmInputs extends Data&lt;ArmInputs&gt; {
 *     &#64;LogName("PositionDeg")
 *     public double position = 0.0;
 * }
 * </pre>
 *
 * <p>The key must be stable across {@code toLog} and {@code fromLog}, which it is, since
 * both are derived from the same annotation. Changing it invalidates replay of older logs.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface LogName {

    /**
     * The literal log key to use for this field, without any leading slash.
     *
     * @return The log key.
     */
    String value();
}
