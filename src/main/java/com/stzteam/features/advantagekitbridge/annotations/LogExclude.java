package com.stzteam.features.advantagekitbridge.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public field of a MARS {@code Data} class as invisible to the AdvantageKit bridge.
 * <p>
 * Excluded fields are neither written during logging nor restored during replay, which
 * makes them non-deterministic in replay. Reserve this for genuinely uninteresting or
 * very large fields (big arrays sampled at 50 Hz), not for values the robot's logic reads.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface LogExclude {}
