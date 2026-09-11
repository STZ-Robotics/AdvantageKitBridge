package com.stzteam.features.advantagekitbridge.internal;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import org.littletonrobotics.junction.LogTable;
import org.littletonrobotics.junction.inputs.LoggableInputs;

import edu.wpi.first.units.Measure;
import edu.wpi.first.util.struct.Struct;
import edu.wpi.first.wpilibj.util.Color;

/**
 * A pre-resolved read/write plan for a single field of a MARS {@code Data} class.
 * <p>
 * One codec is compiled per field the first time a {@code Data} class is seen (see
 * {@link LogCodecFactory}) and then reused for the lifetime of the program. All the
 * expensive decisions -- which {@code LogTable} overload applies, which
 * {@link Struct} serialises this type, what the log key is -- are made once, at compile
 * time, so the per-loop cost is a {@link Field} access plus one typed {@code LogTable}
 * call. The primitive codecs use {@code Field.getDouble} and friends specifically to avoid
 * boxing a value per field per subsystem per cycle, which on a roboRIO is the difference
 * between a quiet heap and a GC pause mid-match.
 */
public abstract class LogCodec {

    /** The field this codec reads from and writes to. */
    protected final Field field;

    /** The AdvantageKit log key this field is stored under, relative to its table. */
    protected final String key;

    /**
     * @param field The target field, which must be public, non-static and non-transient.
     * @param key   The log key to use.
     */
    protected LogCodec(Field field, String key) {
        this.field = field;
        this.key = key;
    }

    /**
     * Writes this field's current value into the table. Called every loop while logging.
     *
     * @param table The subsystem's input table.
     * @param owner The {@code Data} instance holding the field.
     * @throws IllegalAccessException If the field became inaccessible.
     */
    public abstract void toLog(LogTable table, Object owner) throws IllegalAccessException;

    /**
     * Restores this field from the table. Called every loop while replaying. If the key is
     * absent from the log, the field keeps its current value.
     *
     * @param table The subsystem's input table.
     * @param owner The {@code Data} instance holding the field.
     * @throws IllegalAccessException If the field became inaccessible.
     */
    public abstract void fromLog(LogTable table, Object owner) throws IllegalAccessException;

    /** @return The log key this codec reads and writes. */
    public final String key() {
        return key;
    }

    /** @return The field this codec is bound to. */
    public final Field field() {
        return field;
    }

    // ================================================================================
    //  Generic bridges into LogTable
    //
    //  LogTable overloads a lot of methods on erasure-compatible signatures. Calling them
    //  with a raw Struct<Object> and an Object[] would bind to put(key, Struct<T>, T) --
    //  serialising the whole array as if it were one struct. Routing through these tiny
    //  generic methods forces the intended overload: inside them the value is typed T[],
    //  which the scalar overload cannot accept.
    // ================================================================================

    private static <T> void putStruct(LogTable table, String key, Struct<T> struct, T value) {
        table.put(key, struct, value);
    }

    private static <T> T getStruct(LogTable table, String key, Struct<T> struct, T defaultValue) {
        return table.get(key, struct, defaultValue);
    }

    private static <T> void putStructArray(LogTable table, String key, Struct<T> struct, T[] value) {
        table.put(key, struct, value);
    }

    private static <T> T[] getStructArray(LogTable table, String key, Struct<T> struct, T[] defaultValue) {
        return table.get(key, struct, defaultValue);
    }

    private static <T> void putStructArray2d(LogTable table, String key, Struct<T> struct, T[][] value) {
        table.put(key, struct, value);
    }

    private static <T> T[][] getStructArray2d(LogTable table, String key, Struct<T> struct, T[][] defaultValue) {
        return table.get(key, struct, defaultValue);
    }

    private static <R extends Record> void putRecord(LogTable table, String key, R value) {
        table.put(key, value);
    }

    private static <R extends Record> R getRecord(LogTable table, String key, R defaultValue) {
        return table.get(key, defaultValue);
    }

    private static <R extends Record> void putRecordArray(LogTable table, String key, R[] value) {
        table.put(key, value);
    }

    private static <R extends Record> R[] getRecordArray(LogTable table, String key, R[] defaultValue) {
        return table.get(key, defaultValue);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Enum<?> enumValueOf(Class<?> enumType, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumType, name);
    }

    // ================================================================================
    //  Primitive codecs (no boxing)
    // ================================================================================

    /** Codec for a {@code double} field, optionally carrying a unit for AdvantageScope. */
    static final class DoubleCodec extends LogCodec {
        private final String unit;

        DoubleCodec(Field field, String key, String unit) {
            super(field, key);
            this.unit = unit;
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            double value = field.getDouble(owner);
            if (unit == null) {
                table.put(key, value);
            } else {
                table.put(key, value, unit);
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            field.setDouble(owner, table.get(key, field.getDouble(owner)));
        }
    }

    /** Codec for a {@code float} field, optionally carrying a unit for AdvantageScope. */
    static final class FloatCodec extends LogCodec {
        private final String unit;

        FloatCodec(Field field, String key, String unit) {
            super(field, key);
            this.unit = unit;
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            float value = field.getFloat(owner);
            if (unit == null) {
                table.put(key, value);
            } else {
                table.put(key, value, unit);
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            field.setFloat(owner, table.get(key, field.getFloat(owner)));
        }
    }

    /** Codec for a {@code boolean} field. */
    static final class BooleanCodec extends LogCodec {
        BooleanCodec(Field field, String key) {
            super(field, key);
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            table.put(key, field.getBoolean(owner));
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            field.setBoolean(owner, table.get(key, field.getBoolean(owner)));
        }
    }

    /** Codec for a {@code long} field. */
    static final class LongCodec extends LogCodec {
        LongCodec(Field field, String key) {
            super(field, key);
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            table.put(key, field.getLong(owner));
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            field.setLong(owner, table.get(key, field.getLong(owner)));
        }
    }

    /** Codec for an {@code int} field. */
    static final class IntCodec extends LogCodec {
        IntCodec(Field field, String key) {
            super(field, key);
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            table.put(key, field.getInt(owner));
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            field.setInt(owner, table.get(key, field.getInt(owner)));
        }
    }

    /**
     * Codec for {@code short}, {@code byte} and {@code char} fields, all of which widen
     * losslessly into AdvantageKit's integer type and narrow back on replay.
     */
    static final class NarrowIntCodec extends LogCodec {
        /** Which narrow primitive this codec is bound to. */
        enum Kind {
            /** A {@code short} field. */
            SHORT,
            /** A {@code byte} field. */
            BYTE,
            /** A {@code char} field. */
            CHAR
        }

        private final Kind kind;

        NarrowIntCodec(Field field, String key, Kind kind) {
            super(field, key);
            this.kind = kind;
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            switch (kind) {
                case SHORT -> table.put(key, (int) field.getShort(owner));
                case BYTE -> table.put(key, (int) field.getByte(owner));
                case CHAR -> table.put(key, (int) field.getChar(owner));
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            switch (kind) {
                case SHORT -> field.setShort(owner, (short) table.get(key, (int) field.getShort(owner)));
                case BYTE -> field.setByte(owner, (byte) table.get(key, (int) field.getByte(owner)));
                case CHAR -> field.setChar(owner, (char) table.get(key, (int) field.getChar(owner)));
            }
        }
    }

    // ================================================================================
    //  Boxed scalar codecs
    // ================================================================================

    /**
     * Codec for boxed numeric and boolean wrappers. A null value is skipped on write (the
     * key simply does not appear that cycle) and treated as zero/false when producing the
     * fallback for a replay read.
     */
    static final class BoxedCodec extends LogCodec {
        /** Which wrapper type this codec is bound to. */
        enum Kind {
            /** {@link Double}. */
            DOUBLE,
            /** {@link Float}. */
            FLOAT,
            /** {@link Long}. */
            LONG,
            /** {@link Integer}. */
            INTEGER,
            /** {@link Short}. */
            SHORT,
            /** {@link Byte}. */
            BYTE,
            /** {@link Character}. */
            CHARACTER,
            /** {@link Boolean}. */
            BOOLEAN
        }

        private final Kind kind;
        private final String unit;

        BoxedCodec(Field field, String key, Kind kind, String unit) {
            super(field, key);
            this.kind = kind;
            this.unit = unit;
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            Object value = field.get(owner);
            if (value == null) {
                return;
            }
            switch (kind) {
                case DOUBLE -> {
                    double d = (Double) value;
                    if (unit == null) {
                        table.put(key, d);
                    } else {
                        table.put(key, d, unit);
                    }
                }
                case FLOAT -> {
                    float f = (Float) value;
                    if (unit == null) {
                        table.put(key, f);
                    } else {
                        table.put(key, f, unit);
                    }
                }
                case LONG -> table.put(key, ((Long) value).longValue());
                case INTEGER -> table.put(key, ((Integer) value).intValue());
                case SHORT -> table.put(key, (int) (Short) value);
                case BYTE -> table.put(key, (int) (Byte) value);
                case CHARACTER -> table.put(key, (int) (Character) value);
                case BOOLEAN -> table.put(key, ((Boolean) value).booleanValue());
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            Object current = field.get(owner);
            switch (kind) {
                case DOUBLE -> field.set(owner,
                        Double.valueOf(table.get(key, current == null ? 0.0 : (Double) current)));
                case FLOAT -> field.set(owner,
                        Float.valueOf(table.get(key, current == null ? 0.0f : (Float) current)));
                case LONG -> field.set(owner,
                        Long.valueOf(table.get(key, current == null ? 0L : (Long) current)));
                case INTEGER -> field.set(owner,
                        Integer.valueOf(table.get(key, current == null ? 0 : (Integer) current)));
                case SHORT -> field.set(owner,
                        Short.valueOf((short) table.get(key, current == null ? 0 : (int) (Short) current)));
                case BYTE -> field.set(owner,
                        Byte.valueOf((byte) table.get(key, current == null ? 0 : (int) (Byte) current)));
                case CHARACTER -> field.set(owner,
                        Character.valueOf((char) table.get(key, current == null ? 0 : (int) (Character) current)));
                case BOOLEAN -> field.set(owner,
                        Boolean.valueOf(table.get(key, current != null && (Boolean) current)));
            }
        }
    }

    /** Codec for a {@link String} field. */
    static final class StringCodec extends LogCodec {
        StringCodec(Field field, String key) {
            super(field, key);
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            String value = (String) field.get(owner);
            if (value != null) {
                table.put(key, value);
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            field.set(owner, table.get(key, (String) field.get(owner)));
        }
    }

    /**
     * Codec for an enum field, stored as the constant's name.
     * <p>
     * This mirrors byte-for-byte what {@code LogTable.put(key, Enum)} writes, but resolves
     * the constant against the <i>declared</i> field type rather than the value's runtime
     * class. That matters for enums whose constants have bodies: each such constant is an
     * anonymous subclass, and {@code Enum.valueOf} on the subclass throws.
     */
    static final class EnumCodec extends LogCodec {
        private final Class<?> enumType;

        EnumCodec(Field field, String key, Class<?> enumType) {
            super(field, key);
            this.enumType = enumType;
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            Object value = field.get(owner);
            if (value != null) {
                table.put(key, ((Enum<?>) value).name());
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            String name = table.get(key, (String) null);
            if (name == null) {
                return;
            }
            try {
                field.set(owner, enumValueOf(enumType, name));
            } catch (IllegalArgumentException e) {
                BridgeLog.warnOnce(
                        enumType.getName() + "#" + name,
                        "Log holds \"" + name + "\" for field \"" + key + "\", which is no longer a "
                                + enumType.getSimpleName() + " constant. Keeping the current value.");
            }
        }
    }

    /** Codec for an array of enums, stored as a string array. */
    static final class EnumArrayCodec extends LogCodec {
        private final Class<?> enumType;

        EnumArrayCodec(Field field, String key, Class<?> enumType) {
            super(field, key);
            this.enumType = enumType;
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            Object[] value = (Object[]) field.get(owner);
            if (value == null) {
                return;
            }
            String[] names = new String[value.length];
            for (int i = 0; i < value.length; i++) {
                names[i] = value[i] == null ? "" : ((Enum<?>) value[i]).name();
            }
            table.put(key, names);
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            String[] names = table.get(key, (String[]) null);
            if (names == null) {
                return;
            }
            Object result = Array.newInstance(enumType, names.length);
            for (int i = 0; i < names.length; i++) {
                if (names[i] == null || names[i].isEmpty()) {
                    continue;
                }
                try {
                    Array.set(result, i, enumValueOf(enumType, names[i]));
                } catch (IllegalArgumentException e) {
                    BridgeLog.warnOnce(
                            enumType.getName() + "#" + names[i],
                            "Log holds \"" + names[i] + "\" in array \"" + key + "\", which is no longer a "
                                    + enumType.getSimpleName() + " constant. Leaving that slot null.");
                }
            }
            field.set(owner, result);
        }
    }

    /**
     * Codec for a {@link Color} field, stored as a hex string.
     * <p>
     * Same representation {@code LogTable.put(key, Color)} uses, so MARS diagnostic colours
     * and hand-logged colours read identically in AdvantageScope.
     */
    static final class ColorCodec extends LogCodec {
        ColorCodec(Field field, String key) {
            super(field, key);
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            Color value = (Color) field.get(owner);
            if (value != null) {
                table.put(key, value.toHexString());
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            String hex = table.get(key, (String) null);
            if (hex != null && !hex.isEmpty()) {
                field.set(owner, new Color(hex));
            }
        }
    }

    /**
     * Codec for a {@link Measure} field.
     * <p>
     * Written as its magnitude in base units plus the base unit's name as metadata, which is
     * exactly what {@code LogTable.put(key, Measure)} does. On replay the value is rebuilt
     * through the current value's unit, so a null field cannot be restored -- a
     * {@code Measure} field must be initialised for replay to reach it.
     */
    static final class MeasureCodec extends LogCodec {
        MeasureCodec(Field field, String key) {
            super(field, key);
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            Measure<?> value = (Measure<?>) field.get(owner);
            if (value != null) {
                table.put(key, new LogTable.LogValue(
                        value.baseUnitMagnitude(), null, value.baseUnit().name()));
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            Measure<?> current = (Measure<?>) field.get(owner);
            if (current == null) {
                BridgeLog.warnOnce(
                        field.getDeclaringClass().getName() + "." + field.getName() + "#nullMeasure",
                        "Measure field \"" + key + "\" is null, so its unit is unknown and it cannot be "
                                + "replayed. Initialise it in the Data class to make replay deterministic.");
                return;
            }
            double magnitude = table.get(key, current.baseUnitMagnitude());
            field.set(owner, current.unit().ofBaseUnits(magnitude));
        }
    }

    // ================================================================================
    //  Array codecs
    // ================================================================================

    /**
     * Codec for the primitive and string array shapes AdvantageKit stores natively, in one
     * and two dimensions. A single class with a kind switch keeps this readable: array
     * fields already allocate on every read, so the branch is free relative to the copy.
     */
    static final class ArrayCodec extends LogCodec {
        /** Which array shape this codec is bound to. */
        enum Kind {
            /** {@code byte[]}, stored as AdvantageKit's raw type. */
            BYTE,
            /** {@code boolean[]}. */
            BOOLEAN,
            /** {@code int[]}. */
            INT,
            /** {@code long[]}. */
            LONG,
            /** {@code float[]}. */
            FLOAT,
            /** {@code double[]}. */
            DOUBLE,
            /** {@code String[]}. */
            STRING,
            /** {@code byte[][]}. */
            BYTE_2D,
            /** {@code boolean[][]}. */
            BOOLEAN_2D,
            /** {@code int[][]}. */
            INT_2D,
            /** {@code long[][]}. */
            LONG_2D,
            /** {@code float[][]}. */
            FLOAT_2D,
            /** {@code double[][]}. */
            DOUBLE_2D,
            /** {@code String[][]}. */
            STRING_2D
        }

        private final Kind kind;
        private final Object empty;

        ArrayCodec(Field field, String key, Kind kind) {
            super(field, key);
            this.kind = kind;
            this.empty = newEmpty(field.getType());
        }

        private static Object newEmpty(Class<?> arrayType) {
            // A zero-length array still carries the right component type, which is all
            // LogTable needs -- including for 2D values, where it reads the component type
            // off the default to rebuild each row.
            return Array.newInstance(arrayType.getComponentType(), 0);
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            Object value = field.get(owner);
            if (value == null) {
                return;
            }
            switch (kind) {
                case BYTE -> table.put(key, (byte[]) value);
                case BOOLEAN -> table.put(key, (boolean[]) value);
                case INT -> table.put(key, (int[]) value);
                case LONG -> table.put(key, (long[]) value);
                case FLOAT -> table.put(key, (float[]) value);
                case DOUBLE -> table.put(key, (double[]) value);
                case STRING -> table.put(key, (String[]) value);
                case BYTE_2D -> table.put(key, (byte[][]) value);
                case BOOLEAN_2D -> table.put(key, (boolean[][]) value);
                case INT_2D -> table.put(key, (int[][]) value);
                case LONG_2D -> table.put(key, (long[][]) value);
                case FLOAT_2D -> table.put(key, (float[][]) value);
                case DOUBLE_2D -> table.put(key, (double[][]) value);
                case STRING_2D -> table.put(key, (String[][]) value);
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            Object current = field.get(owner);
            Object fallback = current != null ? current : empty;
            switch (kind) {
                case BYTE -> field.set(owner, table.get(key, (byte[]) fallback));
                case BOOLEAN -> field.set(owner, table.get(key, (boolean[]) fallback));
                case INT -> field.set(owner, table.get(key, (int[]) fallback));
                case LONG -> field.set(owner, table.get(key, (long[]) fallback));
                case FLOAT -> field.set(owner, table.get(key, (float[]) fallback));
                case DOUBLE -> field.set(owner, table.get(key, (double[]) fallback));
                case STRING -> field.set(owner, table.get(key, (String[]) fallback));
                case BYTE_2D -> field.set(owner, table.get(key, (byte[][]) fallback));
                case BOOLEAN_2D -> field.set(owner, table.get(key, (boolean[][]) fallback));
                case INT_2D -> field.set(owner, table.get(key, (int[][]) fallback));
                case LONG_2D -> field.set(owner, table.get(key, (long[][]) fallback));
                case FLOAT_2D -> field.set(owner, table.get(key, (float[][]) fallback));
                case DOUBLE_2D -> field.set(owner, table.get(key, (double[][]) fallback));
                case STRING_2D -> field.set(owner, table.get(key, (String[][]) fallback));
            }
        }
    }

    // ================================================================================
    //  Struct codecs
    // ================================================================================

    /**
     * Codec for any type WPILib can struct-serialise: {@code Pose2d}, {@code ChassisSpeeds},
     * {@code SwerveModuleState}, or a team's own type following the same convention
     * ForgeMini already relies on -- a {@code public static final Struct<T> struct} field.
     * <p>
     * The struct is resolved from the <i>declared</i> field type, so replay works even when
     * the field is currently null.
     */
    static final class StructCodec extends LogCodec {
        private final Struct<Object> struct;

        StructCodec(Field field, String key, Struct<Object> struct) {
            super(field, key);
            this.struct = struct;
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            Object value = field.get(owner);
            if (value != null) {
                putStruct(table, key, struct, value);
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            field.set(owner, getStruct(table, key, struct, field.get(owner)));
        }
    }

    /** Codec for an array of struct-serialisable values, e.g. {@code SwerveModuleState[]}. */
    static final class StructArrayCodec extends LogCodec {
        private final Struct<Object> struct;
        private final Object[] empty;

        StructArrayCodec(Field field, String key, Struct<Object> struct, Class<?> componentType) {
            super(field, key);
            this.struct = struct;
            this.empty = (Object[]) Array.newInstance(componentType, 0);
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            Object[] value = (Object[]) field.get(owner);
            if (value != null) {
                putStructArray(table, key, struct, value);
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            Object[] current = (Object[]) field.get(owner);
            field.set(owner, getStructArray(table, key, struct, current != null ? current : empty));
        }
    }

    /** Codec for a 2D array of struct-serialisable values. */
    static final class StructArray2dCodec extends LogCodec {
        private final Struct<Object> struct;
        private final Object[][] empty;

        StructArray2dCodec(Field field, String key, Struct<Object> struct, Class<?> componentType) {
            super(field, key);
            this.struct = struct;
            this.empty = (Object[][]) Array.newInstance(componentType, 0, 0);
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            Object[][] value = (Object[][]) field.get(owner);
            if (value != null) {
                putStructArray2d(table, key, struct, value);
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            Object[][] current = (Object[][]) field.get(owner);
            field.set(owner, getStructArray2d(table, key, struct, current != null ? current : empty));
        }
    }

    // ================================================================================
    //  Record codecs
    // ================================================================================

    /**
     * Codec for a {@link Record} field. AdvantageKit derives a struct layout from the record
     * components on first use, so nothing is required of the record itself.
     */
    static final class RecordCodec extends LogCodec {
        RecordCodec(Field field, String key) {
            super(field, key);
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            Record value = (Record) field.get(owner);
            if (value != null) {
                putRecord(table, key, value);
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            Record current = (Record) field.get(owner);
            if (current == null) {
                BridgeLog.warnOnce(
                        field.getDeclaringClass().getName() + "." + field.getName() + "#nullRecord",
                        "Record field \"" + key + "\" is null, so its layout is unknown and it cannot be "
                                + "replayed. Initialise it in the Data class to make replay deterministic.");
                return;
            }
            field.set(owner, getRecord(table, key, current));
        }
    }

    /** Codec for an array of records. */
    static final class RecordArrayCodec extends LogCodec {
        private final Record[] empty;

        RecordArrayCodec(Field field, String key, Class<?> componentType) {
            super(field, key);
            this.empty = (Record[]) Array.newInstance(componentType, 0);
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            Record[] value = (Record[]) field.get(owner);
            if (value != null) {
                putRecordArray(table, key, value);
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            Record[] current = (Record[]) field.get(owner);
            field.set(owner, getRecordArray(table, key, current != null ? current : empty));
        }
    }

    // ================================================================================
    //  Nested codecs
    // ================================================================================

    /**
     * Codec for a field that is itself a {@link LoggableInputs} -- typically a class
     * generated by AdvantageKit's {@code @AutoLog} processor. The nested object handles its
     * own serialisation into a subtable, so the bridge stays out of the way entirely.
     */
    static final class NestedInputsCodec extends LogCodec {
        NestedInputsCodec(Field field, String key) {
            super(field, key);
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            LoggableInputs value = (LoggableInputs) field.get(owner);
            if (value != null) {
                value.toLog(table.getSubtable(key));
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            LoggableInputs current = (LoggableInputs) field.get(owner);
            if (current != null) {
                current.fromLog(table.getSubtable(key));
            }
        }
    }

    /**
     * Codec for a plain object field, recursed into as its own subtable. Only used when
     * {@code logNestedObjects} is enabled, since following arbitrary references from a
     * {@code Data} class can otherwise pull in half the robot.
     */
    static final class NestedObjectCodec extends LogCodec {
        private final LogCodec[] children;

        NestedObjectCodec(Field field, String key, LogCodec[] children) {
            super(field, key);
            this.children = children;
        }

        @Override
        public void toLog(LogTable table, Object owner) throws IllegalAccessException {
            Object value = field.get(owner);
            if (value == null) {
                return;
            }
            LogTable subtable = table.getSubtable(key);
            for (LogCodec child : children) {
                child.toLog(subtable, value);
            }
        }

        @Override
        public void fromLog(LogTable table, Object owner) throws IllegalAccessException {
            Object value = field.get(owner);
            if (value == null) {
                return;
            }
            LogTable subtable = table.getSubtable(key);
            for (LogCodec child : children) {
                child.fromLog(subtable, value);
            }
        }
    }
}
