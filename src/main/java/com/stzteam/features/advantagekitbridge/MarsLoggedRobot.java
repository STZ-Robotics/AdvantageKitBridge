package com.stzteam.features.advantagekitbridge;

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
 *   <li>Receivers and replay source next, since AdvantageKit only accepts them before the
 *       logger starts.</li>
 *   <li>{@code Logger.start()} before the first subsystem exists, so no input snapshot is
 *       produced while the logger is still closed.</li>
 * </ol>
 *
 * <h2>Replay</h2>
 * In {@link RunMode#REPLAY} the loop is switched off its real-time timer so the log is
 * consumed as fast as the machine allows. Remember that AdvantageKit refuses to replay with
 * HAL simulation extensions loaded: uncheck the sim GUI and DriverStation in the VS Code
 * simulation dialog.
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
     * Boots in an explicit run mode with a custom bridge configuration.
     *
     * @param mode   The MARS run mode.
     * @param config How the bridge names keys and what extras it logs.
     */
    protected MarsLoggedRobot(RunMode mode, BridgeConfig config) {
        this(mode, config, LoggedRobot.defaultPeriodSecs);
    }

    /**
     * Boots in an explicit run mode with a custom configuration and loop period.
     *
     * @param mode          The MARS run mode.
     * @param config        How the bridge names keys and what extras it logs.
     * @param periodSeconds The robot loop period, normally {@code 0.02}.
     */
    protected MarsLoggedRobot(RunMode mode, BridgeConfig config, double periodSeconds) {
        super(periodSeconds);

        this.runMode = mode;

        Environment.setMode(mode);
        AdvantageKitBridge.configure(config);
        AdvantageKitBridge.configureLogger(mode);

        if (mode == RunMode.REPLAY) {
            // Run the log through as fast as possible instead of pacing it at 50 Hz.
            setUseTiming(false);
        }

        Logger.start();
        AdvantageKitBridge.install();
    }

    /** @return The run mode this robot booted in. */
    public final RunMode getRunMode() {
        return runMode;
    }
}
