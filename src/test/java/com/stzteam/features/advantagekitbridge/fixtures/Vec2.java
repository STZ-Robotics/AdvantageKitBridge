package com.stzteam.features.advantagekitbridge.fixtures;

import java.nio.ByteBuffer;
import java.util.Objects;

import edu.wpi.first.util.struct.Struct;

/**
 * A team-defined type written to ForgeMini's convention: a {@code public static final}
 * {@code struct} field, and deliberately <b>no</b> {@code StructSerializable} marker.
 * <p>
 * WPILib's own struct fetcher refuses this shape, so it is exactly the case that proves the
 * bridge's fallback lookup is what makes existing MARS data classes work unmodified.
 */
public final class Vec2 {

    /** X component. */
    public final double x;

    /** Y component. */
    public final double y;

    /**
     * @param x X component.
     * @param y Y component.
     */
    public Vec2(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /** Struct serialiser, discovered reflectively by name. */
    public static final Struct<Vec2> struct = new Struct<Vec2>() {

        @Override
        public Class<Vec2> getTypeClass() {
            return Vec2.class;
        }

        @Override
        public String getTypeName() {
            return "Vec2";
        }

        @Override
        public int getSize() {
            return kSizeDouble * 2;
        }

        @Override
        public String getSchema() {
            return "double x;double y";
        }

        @Override
        public Vec2 unpack(ByteBuffer bb) {
            return new Vec2(bb.getDouble(), bb.getDouble());
        }

        @Override
        public void pack(ByteBuffer bb, Vec2 value) {
            bb.putDouble(value.x);
            bb.putDouble(value.y);
        }
    };

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Vec2 other)) {
            return false;
        }
        return Double.compare(x, other.x) == 0 && Double.compare(y, other.y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "Vec2(" + x + ", " + y + ")";
    }
}
