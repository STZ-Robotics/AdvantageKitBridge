package com.stzteam.features.advantagekitbridge.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.littletonrobotics.junction.inputs.LoggableInputs;

import com.stzteam.features.advantagekitbridge.BridgeConfig;
import com.stzteam.features.advantagekitbridge.annotations.LogExclude;
import com.stzteam.features.advantagekitbridge.annotations.LogName;
import com.stzteam.features.advantagekitbridge.annotations.LogUnit;

import edu.wpi.first.units.Measure;
import edu.wpi.first.util.struct.Struct;
import edu.wpi.first.util.struct.StructFetcher;
import edu.wpi.first.wpilibj.util.Color;

/**
 * Compiles a MARS {@code Data} class into the flat list of {@link LogCodec}s that moves it
 * in and out of an AdvantageKit {@code LogTable}.
 * <p>
 * Compilation happens once per class and is cached, so the reflection cost is paid during
 * the first loop a subsystem runs -- while the robot is disabled -- and never again.
 * <p>
 * The field convention is deliberately the same one ForgeMini's {@code NetworkIO} already
 * uses: <b>public instance fields</b>, with complex types carrying a static
 * {@code Struct} field. That is why an existing MARS subsystem gains AdvantageKit logging
 * and replay without a single edit to its {@code Data} class.
 */
public final class LogCodecFactory {

    private static final Map<CacheKey, LogCodec[]> cache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Struct<?>>> structCache = new ConcurrentHashMap<>();

    /**
     * Identifies a compiled plan. The class alone is not enough: two configurations can ask
     * for the same class and expect different keys, which is exactly what happens when a
     * one-off {@link com.stzteam.features.advantagekitbridge.MarsLoggableInput} is built with
     * its own configuration alongside the globally installed bridge.
     */
    private record CacheKey(
            Class<?> clazz,
            BridgeConfig.KeyStyle keyStyle,
            boolean logNestedObjects,
            int maxNestingDepth) {

        static CacheKey of(Class<?> clazz, BridgeConfig config) {
            return new CacheKey(
                    clazz, config.keyStyle(), config.logNestedObjects(), config.maxNestingDepth());
        }
    }

    private LogCodecFactory() {}

    /**
     * Returns the codecs for a class, compiling and caching them on first use.
     *
     * @param clazz  The {@code Data} class (or any plain object following the convention).
     * @param config The active bridge configuration.
     * @return One codec per loggable field. Never null; possibly empty.
     */
    public static LogCodec[] forClass(Class<?> clazz, BridgeConfig config) {
        CacheKey key = CacheKey.of(clazz, config);
        LogCodec[] cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        LogCodec[] compiled = compile(clazz, config, new ArrayDeque<>());
        cache.put(key, compiled);
        return compiled;
    }

    /**
     * Drops every cached compilation. Called when the bridge is reconfigured, since the
     * codecs bake in the key style, and by tests that need a clean slate.
     */
    public static void invalidate() {
        cache.clear();
        structCache.clear();
    }

    /**
     * Converts a field name into a log key using the configured style.
     *
     * @param fieldName The declared field name.
     * @param style     The naming convention.
     * @return The log key.
     */
    public static String toKey(String fieldName, BridgeConfig.KeyStyle style) {
        if (style == BridgeConfig.KeyStyle.RAW || fieldName.isEmpty()) {
            return fieldName;
        }
        char first = fieldName.charAt(0);
        char upper = Character.toUpperCase(first);
        return first == upper ? fieldName : upper + fieldName.substring(1);
    }

    // ================================================================================
    //  Compilation
    // ================================================================================

    private static LogCodec[] compile(Class<?> clazz, BridgeConfig config, Deque<Class<?>> path) {
        List<LogCodec> codecs = new ArrayList<>();
        for (Map.Entry<String, Field> entry : collectFields(clazz, config).entrySet()) {
            LogCodec codec = codecFor(entry.getValue(), entry.getKey(), config, path);
            if (codec != null) {
                codecs.add(codec);
            }
        }
        return codecs.toArray(new LogCodec[0]);
    }

    /**
     * Gathers the loggable fields of a class, keyed by their log key.
     * <p>
     * {@code getFields()} walks the whole hierarchy, so a field shadowed by a subclass
     * shows up twice under one key. The more-derived declaration wins, matching what plain
     * Java field access on the instance would resolve to.
     */
    private static Map<String, Field> collectFields(Class<?> clazz, BridgeConfig config) {
        Map<String, Field> byKey = new LinkedHashMap<>();
        for (Field field : clazz.getFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                continue;
            }
            if (field.isAnnotationPresent(LogExclude.class)) {
                continue;
            }

            LogName nameAnnotation = field.getAnnotation(LogName.class);
            String key = nameAnnotation != null
                    ? nameAnnotation.value()
                    : toKey(field.getName(), config.keyStyle());

            Field existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, field);
                continue;
            }
            if (existing.getDeclaringClass().isAssignableFrom(field.getDeclaringClass())) {
                // The new one is declared further down the hierarchy, so it shadows the old.
                byKey.put(key, field);
            } else if (!field.getDeclaringClass().isAssignableFrom(existing.getDeclaringClass())) {
                // Neither shadows the other, so two unrelated fields want the same key. One of
                // them would be invisible in the log and un-replayable, silently.
                BridgeLog.warnOnce(
                        clazz.getName() + "#collision#" + key,
                        "Fields \"" + existing.getName() + "\" and \"" + field.getName() + "\" in "
                                + clazz.getSimpleName() + " both map to log key \"" + key
                                + "\". Only \"" + existing.getName() + "\" is logged. Use @LogName to "
                                + "give one of them a distinct key.");
            }
        }
        return byKey;
    }

    private static String unitOf(Field field) {
        LogUnit unit = field.getAnnotation(LogUnit.class);
        return unit == null ? null : unit.value();
    }

    private static LogCodec codecFor(Field field, String key, BridgeConfig config, Deque<Class<?>> path) {
        Class<?> type = field.getType();

        // --- primitives, unboxed so the hot path never allocates ---
        if (type == double.class) {
            return new LogCodec.DoubleCodec(field, key, unitOf(field));
        }
        if (type == float.class) {
            return new LogCodec.FloatCodec(field, key, unitOf(field));
        }
        if (type == boolean.class) {
            return new LogCodec.BooleanCodec(field, key);
        }
        if (type == long.class) {
            return new LogCodec.LongCodec(field, key);
        }
        if (type == int.class) {
            return new LogCodec.IntCodec(field, key);
        }
        if (type == short.class) {
            return new LogCodec.NarrowIntCodec(field, key, LogCodec.NarrowIntCodec.Kind.SHORT);
        }
        if (type == byte.class) {
            return new LogCodec.NarrowIntCodec(field, key, LogCodec.NarrowIntCodec.Kind.BYTE);
        }
        if (type == char.class) {
            return new LogCodec.NarrowIntCodec(field, key, LogCodec.NarrowIntCodec.Kind.CHAR);
        }

        // --- boxed scalars ---
        if (type == Double.class) {
            return new LogCodec.BoxedCodec(field, key, LogCodec.BoxedCodec.Kind.DOUBLE, unitOf(field));
        }
        if (type == Float.class) {
            return new LogCodec.BoxedCodec(field, key, LogCodec.BoxedCodec.Kind.FLOAT, unitOf(field));
        }
        if (type == Long.class) {
            return new LogCodec.BoxedCodec(field, key, LogCodec.BoxedCodec.Kind.LONG, null);
        }
        if (type == Integer.class) {
            return new LogCodec.BoxedCodec(field, key, LogCodec.BoxedCodec.Kind.INTEGER, null);
        }
        if (type == Short.class) {
            return new LogCodec.BoxedCodec(field, key, LogCodec.BoxedCodec.Kind.SHORT, null);
        }
        if (type == Byte.class) {
            return new LogCodec.BoxedCodec(field, key, LogCodec.BoxedCodec.Kind.BYTE, null);
        }
        if (type == Character.class) {
            return new LogCodec.BoxedCodec(field, key, LogCodec.BoxedCodec.Kind.CHARACTER, null);
        }
        if (type == Boolean.class) {
            return new LogCodec.BoxedCodec(field, key, LogCodec.BoxedCodec.Kind.BOOLEAN, null);
        }

        if (type == String.class) {
            return new LogCodec.StringCodec(field, key);
        }
        if (type.isEnum()) {
            return new LogCodec.EnumCodec(field, key, type);
        }
        if (type == Color.class) {
            return new LogCodec.ColorCodec(field, key);
        }
        if (Measure.class.isAssignableFrom(type)) {
            return new LogCodec.MeasureCodec(field, key);
        }

        if (type.isArray()) {
            return arrayCodecFor(field, key, type.getComponentType());
        }

        // A struct beats every other object representation: it is the compact, schema-carrying
        // form AdvantageScope renders natively (poses, chassis speeds, module states).
        Struct<Object> struct = structFor(type);
        if (struct != null) {
            return new LogCodec.StructCodec(field, key, struct);
        }

        if (Record.class.isAssignableFrom(type)) {
            return new LogCodec.RecordCodec(field, key);
        }
        if (LoggableInputs.class.isAssignableFrom(type)) {
            return new LogCodec.NestedInputsCodec(field, key);
        }

        return nestedCodecFor(field, key, type, config, path);
    }

    private static LogCodec arrayCodecFor(Field field, String key, Class<?> component) {
        if (component == byte.class) {
            return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.BYTE);
        }
        if (component == boolean.class) {
            return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.BOOLEAN);
        }
        if (component == int.class) {
            return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.INT);
        }
        if (component == long.class) {
            return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.LONG);
        }
        if (component == float.class) {
            return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.FLOAT);
        }
        if (component == double.class) {
            return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.DOUBLE);
        }
        if (component == String.class) {
            return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.STRING);
        }
        if (component.isEnum()) {
            return new LogCodec.EnumArrayCodec(field, key, component);
        }

        if (component.isArray()) {
            Class<?> inner = component.getComponentType();
            if (inner == byte.class) {
                return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.BYTE_2D);
            }
            if (inner == boolean.class) {
                return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.BOOLEAN_2D);
            }
            if (inner == int.class) {
                return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.INT_2D);
            }
            if (inner == long.class) {
                return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.LONG_2D);
            }
            if (inner == float.class) {
                return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.FLOAT_2D);
            }
            if (inner == double.class) {
                return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.DOUBLE_2D);
            }
            if (inner == String.class) {
                return new LogCodec.ArrayCodec(field, key, LogCodec.ArrayCodec.Kind.STRING_2D);
            }
            Struct<Object> innerStruct = structFor(inner);
            if (innerStruct != null) {
                return new LogCodec.StructArray2dCodec(field, key, innerStruct, inner);
            }
            return unsupported(field, key);
        }

        Struct<Object> componentStruct = structFor(component);
        if (componentStruct != null) {
            return new LogCodec.StructArrayCodec(field, key, componentStruct, component);
        }
        if (Record.class.isAssignableFrom(component)) {
            return new LogCodec.RecordArrayCodec(field, key, component);
        }
        return unsupported(field, key);
    }

    private static LogCodec nestedCodecFor(
            Field field, String key, Class<?> type, BridgeConfig config, Deque<Class<?>> path) {

        if (!config.logNestedObjects()) {
            return unsupported(field, key);
        }
        if (path.size() >= config.maxNestingDepth()) {
            BridgeLog.warnOnce(
                    token(field) + "#depth",
                    "Field \"" + key + "\" of type " + type.getSimpleName() + " is nested deeper than "
                            + "maxNestingDepth (" + config.maxNestingDepth() + "), so it is not logged.");
            return null;
        }
        if (path.contains(type)) {
            BridgeLog.warnOnce(
                    token(field) + "#cycle",
                    "Field \"" + key + "\" of type " + type.getSimpleName()
                            + " would recurse into itself, so it is not logged.");
            return null;
        }
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            BridgeLog.warnOnce(
                    token(field) + "#abstract",
                    "Field \"" + key + "\" is declared as " + type.getSimpleName()
                            + ", which is abstract, so its concrete fields cannot be resolved ahead of "
                            + "time. Declare it as a concrete type to log it.");
            return null;
        }

        path.push(type);
        LogCodec[] children;
        try {
            children = compile(type, config, path);
        } finally {
            path.pop();
        }
        if (children.length == 0) {
            return unsupported(field, key);
        }
        return new LogCodec.NestedObjectCodec(field, key, children);
    }

    private static LogCodec unsupported(Field field, String key) {
        String remedy = field.getType().isArray()
                ? "Use a component type with a public static Struct field or a record"
                : "Give the type a public static Struct field, make it a record, or turn on "
                        + "logNestedObjects to recurse into its fields";

        BridgeLog.warnOnce(
                token(field),
                "No AdvantageKit representation for field \"" + key + "\" of type "
                        + field.getType().getSimpleName() + " in " + field.getDeclaringClass().getSimpleName()
                        + ". It is skipped, which also means it is not restored during replay. " + remedy
                        + ", or mark the field @LogExclude to silence this.");
        return null;
    }

    private static String token(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName();
    }

    // ================================================================================
    //  Struct resolution
    // ================================================================================

    /**
     * Finds the {@link Struct} that serialises a type, or null if there is none.
     * <p>
     * WPILib's own fetcher is tried first, which covers everything implementing
     * {@code StructSerializable} (all of wpimath's geometry and kinematics types). It only
     * looks at classes carrying that marker interface, so the public-static-field lookup
     * behind it is what picks up a team's own types written to ForgeMini's convention,
     * including ones that inherit {@code struct} from a base class.
     *
     * @param type The class to serialise.
     * @return Its struct, cast for use with the raw-typed codecs, or null.
     */
    @SuppressWarnings("unchecked")
    static Struct<Object> structFor(Class<?> type) {
        return (Struct<Object>) structCache
                .computeIfAbsent(type, LogCodecFactory::resolveStruct)
                .orElse(null);
    }

    private static Optional<Struct<?>> resolveStruct(Class<?> type) {
        if (type.isPrimitive()) {
            return Optional.empty();
        }

        try {
            Optional<Struct<?>> fetched = StructFetcher.fetchStructDynamic(type);
            if (fetched.isPresent()) {
                return fetched;
            }
        } catch (Throwable ignored) {
            // Fall through to the reflective lookup below.
        }

        Struct<?> viaPublicField = readStructField(type, true);
        if (viaPublicField != null) {
            return Optional.of(viaPublicField);
        }
        Struct<?> viaDeclaredField = readStructField(type, false);
        return Optional.ofNullable(viaDeclaredField);
    }

    private static Struct<?> readStructField(Class<?> type, boolean publicOnly) {
        try {
            Field field = publicOnly ? type.getField("struct") : type.getDeclaredField("struct");
            if (!Modifier.isStatic(field.getModifiers()) || !Struct.class.isAssignableFrom(field.getType())) {
                return null;
            }
            if (!publicOnly) {
                field.setAccessible(true);
            }
            return (Struct<?>) field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
