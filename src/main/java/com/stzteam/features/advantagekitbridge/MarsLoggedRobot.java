package com.stzteam.features.advantagekitbridge;

import org.littletonrobotics.junction.LogDataReceiver;
import org.littletonrobotics.junction.LoggedRobot;
import org.littletonrobotics.junction.Logger;

import com.stzteam.mars.builder.Environment;
import com.stzteam.mars.builder.Environment.RunMode;

/**
 * A {@link LoggedRobot} that boots MARS and AdvantageKit together.
 * <p>
 * AdvantageKit requires the robot class to extend {@code LoggedRobot} rather than
 * {@code TimedRobot}, and requires its receivers, replay source, timing mode and
 * {@code Logger.start()} to be sequenced correctly before any subsystem is constructed.
 * This class does all of it from one constructor argument -- the MARS {@link RunMode} --
 * so a MARS robot moves onto AdvantageKit by changing which class {@code Robot} extends.
 *
 * <pre>
 * public class Robot extends MarsLoggedRobot {
 *
 *     private final IRobotContainer container;
 *
 *     public Robot() {
 *         super(Manifest.CURRENT_MODE);
 *
 *         // From here on everything is ordinary MARS: the global run mode is set, the
 *         // logger is running, and the bridge is already listening to every subsystem
 *         // built from this point forward.
 *         DriverStation.silenceJoystickConnectionWarning(true);
 *         container = new RobotContainer();
 *     }
 *
 *     &#64;Override
 *     public void robotPeriodic() {
 *         CommandScheduler.getInstance().run();
 *         container.updateNodes();
 *     }
 * }
 * </pre>
 *
 * <h2>Ordering</h2>
 * Everything here runs in the superclass constructor, therefore before the subclass body.
 * That ordering is load-bearing:
 * <ol>
 *   <li>{@link Environment#setMode(RunMode)} first, so the {@code Injector} picks the right
 *       IO layer for every subsystem the subclass is about to build.</li>
 *   <li>Receivers and replay source next -- the run mode's own, then any passed as
 *       {@code extraReceivers} -- since AdvantageKit only accepts them before the logger
 *       starts.</li>
 *   <li>{@code Logger.start()} before the first subsystem exists, so no input snapshot is
 *       produced while the logger is still closed.</li>
 * </ol>
 *
 * <h2>Extra receivers</h2>
 * Each run mode brings the receivers AdvantageKit's guide prescribes for it -- see
 * {@link AdvantageKitBridge#configureLogger(RunMode)}. Anything beyond that goes in the
 * {@code extraReceivers} parameter, because {@code Logger.start()} has already run by the
 * time the subclass constructor body executes and AdvantageKit rejects receivers after that.
 * <p>
 * The usual reason to want one is recording a {@code .wpilog} from desktop simulation, which
 * {@link RunMode#SIM} does not do on its own:
 *
 * <pre>
 * public Robot() {
 *     super(Manifest.CURRENT_MODE, new WPILOGWriter("logs"));
 * }
 * </pre>
 *
 * Extras are registered in every run mode, so pick per mode if that is not what you want:
 *
 * <pre>
 * public Robot() {
 *     super(Manifest.CURRENT_MODE, simLogger(Manifest.CURRENT_MODE));
 * }
 *
 * private static LogDataReceiver[] simLogger(RunMode mode) {
 *     return mode == RunMode.SIM
 *             ? new LogDataReceiver[] {new WPILOGWriter("logs")}
 *             : new LogDataReceiver[0];
 * }
 * </pre>
 *
 * That helper must be {@code static}: it is evaluated as a {@code super(...)} argument,
 * before the instance exists.
 *
 * <h2>Replay</h2>
 * In {@link RunMode#REPLAY} the loop is switched off its real-time timer so the log is
 * consumed as fast as the machine allows. Remember that AdvantageKit refuses to replay with
 * HAL simulation extensions loaded: uncheck the sim GUI and DriverStation in the VS Code
 * simulation dialog. A {@code .wpilog} recorded in {@code SIM} replays like any other, but
 * note that it holds simulated hardware, so the replay reproduces the simulation rather than
 * a match.
 */
public abstract class MarsLoggedRobot extends LoggedRobot {

    private final RunMode runMode;

    /**
     * Boots with the run mode already set on {@link Environment} and the default
     * configuration.
     */
    protected MarsLoggedRobot() {
        this(Environment.getMode(), BridgeConfig.defaults(), LoggedRobot.defaultPeriodSecs);
    }

    /**
     * Boots in an explicit run mode with the default configuration.
     *
     * @param mode The MARS run mode, typically {@code Manifest.CURRENT_MODE}.
     */
    protected MarsLoggedRobot(RunMode mode) {
        this(mode, BridgeConfig.defaults(), LoggedRobot.defaultPeriodSecs);
    }

    /**
     * Boots in an explicit run mode with the default configuration, plus extra data
     * receivers. See {@linkplain MarsLoggedRobot class docs} for what "extra" means.
     *
     * @param mode            The MARS run mode, typically {@code Manifest.CURRENT_MODE}.
     * @param extraReceivers  Receivers to register in addition to the ones the run mode
     *                        implies. May be empty; must not contain nulls.
     */
    protected MarsLoggedRobot(RunMode mode, LogDataReceiver... extraReceivers) {
        this(mode, BridgeConfig.defaults(), LoggedRobot.defaultPeriodSecs, extraReceivers);
    }

    /**
     * Boots in an explicit run mode with a custom bridge configuration.
     *
     * @param mode   The MARS run mode.
     * @param config How the bridge names keys and what extras it logs.
     */
    protected MarsLoggedRobot(RunMode mode, BridgeConfig config) {
        this(mode, config, LoggedRobot.defaultPeriodSecs);
    }

    /**
     * Boots in an explicit run mode with a custom bridge configuration, plus extra data
     * receivers.
     *
     * @param mode            The MARS run mode.
     * @param config          How the bridge names keys and what extras it logs.
     * @param extraReceivers  Receivers to register in addition to the ones the run mode
     *                        implies. May be empty; must not contain nulls.
     */
    protected MarsLoggedRobot(RunMode mode, BridgeConfig config, LogDataReceiver... extraReceivers) {
        this(mode, config, LoggedRobot.defaultPeriodSecs, extraReceivers);
    }

    /**
     * Boots in an explicit run mode with a custom configuration, loop period and extra data
     * receivers.
     *
     * @param mode            The MARS run mode.
     * @param config          How the bridge names keys and what extras it logs.
     * @param periodSeconds   The robot loop period, normally {@code 0.02}.
     * @param extraReceivers  Receivers to register in addition to the ones the run mode
     *                        implies. May be empty; must not contain nulls.
     */
    protected MarsLoggedRobot(
            RunMode mode, BridgeConfig config, double periodSeconds, LogDataReceiver... extraReceivers) {
        super(periodSeconds);

        this.runMode = mode;

        Environment.setMode(mode);
        AdvantageKitBridge.configure(config);
        AdvantageKitBridge.configureLogger(mode);

        // After configureLogger so the mode's own receivers keep their usual position in the
        // list, and before Logger.start() because that is the last moment AdvantageKit
        // accepts a receiver at all. This is the only window a subclass cannot reach on its
        // own: its constructor body does not run until this one returns.
        addExtraReceivers(extraReceivers);

        if (mode == RunMode.REPLAY) {
            // Run the log through as fast as possible instead of pacing it at 50 Hz.
            setUseTiming(false);
        }

        Logger.start();
        AdvantageKitBridge.install();
    }

    private static void addExtraReceivers(LogDataReceiver[] extraReceivers) {
        if (extraReceivers == null) {
            return;
        }
        for (int i = 0; i < extraReceivers.length; i++) {
            if (extraReceivers[i] == null) {
                throw new IllegalArgumentException("extraReceivers[" + i + "] must not be null");
            }
            Logger.addDataReceiver(extraReceivers[i]);
        }
    }

    /** @return The run mode this robot booted in. */
    public final RunMode getRunMode() {
        return runMode;
    }
}
