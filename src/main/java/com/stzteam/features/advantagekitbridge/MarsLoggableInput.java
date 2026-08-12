package com.stzteam.features.advantagekitbridge;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.littletonrobotics.junction.LogTable;
import org.littletonrobotics.junction.inputs.LoggableInputs;

import edu.wpi.first.util.struct.Struct;

/**
 * Adapts any MARS {@code Data<T>} object into an AdvantageKit {@link LoggableInputs},
 * reusing the same "public fields + optional static {@code .struct} field" convention
 * already used by ForgeMini's {@code NetworkIO}, but targeting AdvantageKit's
 * {@link LogTable} instead of NetworkTables.
 * <p>
 * This lets any existing MARS subsystem gain AdvantageKit logging and replay support
 * with zero changes to its {@code Data} class, as long as its fields already follow the
 * convention ForgeMini expects: public fields, and a public static {@code Struct<T> struct}
 * field for any complex type (Pose2d, ChassisSpeeds, SwerveModuleState, etc.).
 * <p>
 * <b>Note:</b> Field/method signatures on {@link LogTable} can shift slightly between
 * AdvantageKit releases. Verify {@code put}/{@code get} overloads against the exact
 * version pinned in {@code vendordeps/AdvantageKit.json} before relying on this in a match.
 */
public class MarsLoggableInput implements LoggableInputs {

    private static final Map<Class<?>, Field[]> fieldCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Struct<?>> structCache = new ConcurrentHashMap<>();
    private static final Set<String> warnedFields = ConcurrentHashMap.newKeySet();

    private final Object dataObject;

    /**
     * @param dataObject A MARS {@code Data<T>} snapshot (or any plain object following the
     *                    public-fields convention) to bridge into AdvantageKit.
     */
    public MarsLoggableInput(Object dataObject) {
        this.dataObject = dataObject;
    }

    private static Field[] getFields(Class<?> clazz) {
        return fieldCache.computeIfAbsent(clazz, Class::getFields);
    }

    @SuppressWarnings("unchecked")
    private static <T> Struct<T> getStruct(Class<T> clazz) {
        return (Struct<T>) structCache.computeIfAbsent(clazz, c -> {
            try {
                Field f = c.getField("struct");
                return (Struct<?>) f.get(null);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private static void warnOnce(String key, String message) {
        if (warnedFields.add(key)) {
            System.err.println("[ForgeMiniLoggableInputs] " + message);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void toLog(LogTable table) {
        for (Field field : getFields(dataObject.getClass())) {
            try {
                Object value = field.get(dataObject);
                if (value == null) continue;
                String key = field.getName();

                if (value instanceof Double d) table.put(key, d);
                else if (value instanceof Boolean b) table.put(key, b);
                else if (value instanceof Integer i) table.put(key, i);
                else if (value instanceof String s) table.put(key, s);
                else if (value instanceof double[] da) table.put(key, da);
                else if (value instanceof boolean[] ba) table.put(key, ba);
                else if (value instanceof String[] sa) table.put(key, sa);
                else {
                    Struct<Object> struct = (Struct<Object>) getStruct(value.getClass());
                    if (struct != null) {
                        table.put(key, struct, value);
                    } else {
                        warnOnce(key, "No .struct found for field '" + key + "' of type "
                            + value.getClass().getSimpleName() + " — skipped.");
                    }
                }
            } catch (IllegalAccessException e) {
                warnOnce(field.getName(), "Could not read field '" + field.getName() + "': " + e.getMessage());
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void fromLog(LogTable table) {
        for (Field field : getFields(dataObject.getClass())) {
            try {
                String key = field.getName();
                Class<?> type = field.getType();
                Object current = field.get(dataObject);

                if (type == double.class || type == Double.class) {
                    field.set(dataObject, table.get(key, current != null ? (Double) current : 0.0));
                } else if (type == boolean.class || type == Boolean.class) {
                    field.set(dataObject, table.get(key, current != null ? (Boolean) current : false));
                } else if (type == int.class || type == Integer.class) {
                    field.set(dataObject, (int) table.get(key, current != null ? (Integer) current : 0));
                } else if (type == String.class) {
                    field.set(dataObject, table.get(key, current != null ? (String) current : ""));
                } else if (type == double[].class) {
                    field.set(dataObject, table.get(key, current != null ? (double[]) current : new double[0]));
                } else if (type == boolean[].class) {
                    field.set(dataObject, table.get(key, current != null ? (boolean[]) current : new boolean[0]));
                } else if (type == String[].class) {
                    field.set(dataObject, table.get(key, current != null ? (String[]) current : new String[0]));
                } else if (current != null) {
                    Struct<Object> struct = (Struct<Object>) getStruct(type);
                    if (struct != null) {
                        Object replayed = table.get(key, struct, current);
                        field.set(dataObject, replayed);
                    }
                }
            } catch (IllegalAccessException e) {
                warnOnce(field.getName(), "Could not write field '" + field.getName() + "': " + e.getMessage());
            }
        }
    }
}