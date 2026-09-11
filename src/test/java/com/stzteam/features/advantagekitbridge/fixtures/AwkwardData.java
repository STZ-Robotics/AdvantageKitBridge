package com.stzteam.features.advantagekitbridge.fixtures;

/**
 * A snapshot class containing shapes AdvantageKit has no representation for, plus a nested
 * plain object.
 * <p>
 * Exists to prove the bridge degrades safely: a field it cannot serialise is reported once
 * and skipped, and the rest of the snapshot still reaches the log. A robot losing one chart
 * is recoverable; a robot crashing in {@code periodic} is not.
 */
public class AwkwardData {

    /** A nested plain object, only logged when nesting is enabled. */
    public static class Nested {
        /** A value inside the nested object. */
        public double inner = 4.5;
        /** Another value inside the nested object. */
        public String name = "nested";
    }

    /** A type with no struct, no record and no primitive mapping. */
    public static class Opaque {
        /** Not reachable through any AdvantageKit type. */
        public Object payload = new Object();
    }

    /** A perfectly ordinary field that must keep working regardless. */
    public double good = 1.25;

    /** A shape AdvantageKit has no array type for. */
    public short[] unsupportedArray = {1, 2};

    /** A nested plain object. */
    public Nested nested = new Nested();

    /** A reference the bridge cannot serialise at all. */
    public Opaque opaque = new Opaque();
}
