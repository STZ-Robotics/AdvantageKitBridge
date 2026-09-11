package com.stzteam.features.advantagekitbridge;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

import com.stzteam.features.advantagekitbridge.internal.BridgeLog;
import com.stzteam.mars.builder.Environment;
import com.stzteam.mars.builder.Environment.RunMode;
import com.stzteam.mars.builder.Injector;
import com.stzteam.mars.models.singlemodule.IO;

/**
 * Builds an inert {@link IO} implementation for replay.
 * <p>
 * Replay is already correct without this: MARS calls the bridge's hook <i>after</i>
 * {@code updateInputs}, so the recorded values overwrite whatever the IO layer produced.
 * What this class removes is the waste. MARS' {@link Injector} hands {@link RunMode#REPLAY}
 * the simulation IO, so a plain replay would keep stepping physics models whose output is
 * discarded a moment later -- burning the CPU that makes replay faster than real time, and
 * running flywheel and elevator sims that were never part of the match.
 * <p>
 * The replacement is generated at runtime with a {@link Proxy}, so it works for any team's
 * {@code IO} interface without that interface knowing anything about this Feature:
 * <ul>
 *   <li>{@code updateInputs} does nothing, leaving the snapshot for the log to fill.</li>
 *   <li>{@code isFallback()} reports {@code false}. This matters: MARS skips the whole
 *       periodic block of a fallback subsystem, and a skipped block never reaches the hook,
 *       so a fallback here would silently produce an empty replay.</li>
 *   <li>Every other {@code default} method runs its real implementation, so helper logic
 *       written on the interface still behaves.</li>
 *   <li>Every remaining abstract method -- the actuator commands -- returns a zero value and
 *       does nothing, which is what you want when nothing is actually moving.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * Swap {@code Injector.createIO} for the overload here, adding the IO interface so the proxy
 * knows what to implement:
 *
 * <pre>
 * ArmIO io = MarsReplayIO.createIO(
 *     ArmIO.class, HAS_ARM, ArmIOFallback::new, ArmIOKraken::new, ArmIOSim::new);
 * </pre>
 *
 * <h2>Limitation</h2>
 * Only interfaces can be proxied. A {@code CompositeIO}, which is an abstract class, falls
 * back to the simulation implementation; that is harmless, since a composite's children are
 * replayed through their own hooks anyway.
 */
public final class MarsReplayIO {

    private MarsReplayIO() {}

    /**
     * Chooses an IO implementation the way MARS' {@link Injector} does, except that
     * {@link RunMode#REPLAY} gets an inert proxy instead of the simulation layer.
     *
     * @param <T>              The IO interface type.
     * @param ioInterface      The IO interface to implement during replay.
     * @param isEnabled        Whether this module exists on the robot at all.
     * @param fallbackSupplier The dummy implementation used when the module is disabled.
     * @param realSupplier     The hardware implementation, used in {@link RunMode#REAL}.
     * @param simSupplier      The simulation implementation, used in {@link RunMode#SIM}.
     * @return The implementation for the current run mode.
     */
    public static <T> T createIO(
            Class<T> ioInterface,
            boolean isEnabled,
            Supplier<T> fallbackSupplier,
            Supplier<T> realSupplier,
            Supplier<T> simSupplier) {

        if (isEnabled && Environment.getMode() == RunMode.REPLAY && ioInterface.isInterface()) {
            return of(ioInterface);
        }
        return Injector.createIO(isEnabled, fallbackSupplier, realSupplier, simSupplier);
    }

    /**
     * Creates an inert implementation of an IO interface for replay.
     *
     * @param <T>         The IO interface type.
     * @param ioInterface The interface to implement. Must be an interface.
     * @return A proxy that reads no hardware and drives no actuators.
     * @throws IllegalArgumentException If the given type is not an interface.
     */
    @SuppressWarnings("unchecked")
    public static <T> T of(Class<T> ioInterface) {
        if (!ioInterface.isInterface()) {
            throw new IllegalArgumentException(
                    ioInterface.getName() + " is not an interface, so a replay IO cannot be generated "
                            + "for it. Keep using the simulation implementation for this module.");
        }
        if (!IO.class.isAssignableFrom(ioInterface)) {
            BridgeLog.warnOnce(
                    ioInterface.getName() + "#notMarsIo",
                    ioInterface.getSimpleName() + " does not extend the MARS IO interface. A replay "
                            + "proxy is still generated, but MARS will not drive it.");
        }

        return (T) Proxy.newProxyInstance(
                ioInterface.getClassLoader(),
                new Class<?>[] {ioInterface},
                new ReplayHandler(ioInterface));
    }

    /** Answers every call on a replayed IO without touching hardware. */
    private static final class ReplayHandler implements InvocationHandler {

        private final Class<?> ioInterface;

        private ReplayHandler(Class<?> ioInterface) {
            this.ioInterface = ioInterface;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object[] safeArgs = args == null ? new Object[0] : args;
            String name = method.getName();

            if (method.getDeclaringClass() == Object.class) {
                return switch (name) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == safeArgs[0];
                    case "toString" -> "ReplayIO<" + ioInterface.getSimpleName() + ">";
                    default -> null;
                };
            }

            if (name.equals("updateInputs") && safeArgs.length == 1) {
                // The log fills this snapshot a moment from now; writing to it here would
                // only be overwritten.
                return null;
            }
            if (name.equals("isFallback") && safeArgs.length == 0) {
                // Never report fallback: MARS skips a fallback subsystem's entire periodic
                // block, which would keep the replayed inputs from ever reaching the hook.
                return Boolean.FALSE;
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, safeArgs);
            }
            return defaultValueOf(method.getReturnType());
        }

        private static Object defaultValueOf(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return Boolean.FALSE;
            }
            if (returnType == double.class) {
                return 0.0d;
            }
            if (returnType == float.class) {
                return 0.0f;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == char.class) {
                return (char) 0;
            }
            return null;
        }
    }
}
