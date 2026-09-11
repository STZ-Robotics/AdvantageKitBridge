# MARS AdvantageKit Bridge

Official **MARS Framework** Feature (STZ-Robotics) that connects the MARS architecture to
[AdvantageKit](https://docs.advantagekit.org/)'s logging and **replay** pipeline.

One `install()` and **every** `ModularSubsystem` on the robot is logged as AdvantageKit
inputs, with deterministic replay. **You do not have to touch a single existing `Data`
class, `IO` interface or `Request`.**

---

## Why it works without changing anything

MARS already hands every input snapshot to a global hook, right after the hardware is read
and right before any `Request` runs:

```java
// ModularSubsystem.executePeriodicLogic()
actor.updateInputs(inputs);
D data = inputs.snapshot();

for (BiConsumer<String, Data<?>> hook : globalInputHooks) {
    hook.accept(name, data);          // <-- the bridge plugs in here
}

absolutePeriodic(data);
currentRequest.apply(data, actor);
telemetry.telemeterize(data);
```

That is exactly where AdvantageKit needs to sit:

* **While logging**, the snapshot is written exactly as it came off the hardware.
* **While replaying**, the bridge *overwrites* that same snapshot with the values from the
  log. Since MARS passes that object to `absolutePeriodic`, to the `Request` and to the
  `Telemetry`, the whole robot re-decides using the real data from the match.

It works the same way if your `Data` overrides `snapshot()` to return a defensive copy (like
`TurretInputs` does in the MARS base robot), because MARS passes that same copy to the hook.

Serialization reuses the **convention ForgeMini already uses** in `NetworkIO`: public fields,
and complex types carrying a `public static final Struct<T> struct` field. That is why a
`Data` class that publishes correctly through `NetworkIO` today logs correctly to
AdvantageKit without being edited.

---

## Adding AdvantageKit to your MARS project

This Feature is the bridge, not AdvantageKit itself. AdvantageKit ships a native library
(`akit-wpilibio`) that only a real WPILib vendordep can deliver, so it has to be installed
the normal way first. These are the four steps, start to finish.

### Step 1 — Install the AdvantageKit vendordep

In VS Code, open the command palette and run **WPILib: Manage Vendor Libraries** →
**Install new libraries (online)**, then paste:

```
https://github.com/Mechanical-Advantage/AdvantageKit/releases/latest/download/AdvantageKit.json
```

That drops `vendordeps/AdvantageKit.json` into your robot project. Pin the version you
compete with; do not let it float mid-season.

### Step 2 — Add the AdvantageKit blocks to the robot's `build.gradle`

AdvantageKit's annotation processor is not delivered by the vendordep, so it has to be
declared by hand. Add the `replayWatch` task at the top level, and the `annotationProcessor`
line **inside your existing `dependencies` block**:

```groovy
task(replayWatch, type: JavaExec) {
    mainClass = "org.littletonrobotics.junction.ReplayWatch"
    classpath = sourceSets.main.runtimeClasspath
}

dependencies {
    // ... everything you already have ...

    def akitJson = new groovy.json.JsonSlurper()
        .parseText(new File(projectDir.getAbsolutePath() + "/vendordeps/AdvantageKit.json").text)
    annotationProcessor "org.littletonrobotics.akit:akit-autolog:$akitJson.version"
}
```

The `replayWatch` task is optional but worth having: it re-runs a replay automatically every
time you rebuild.

> You only need the annotation processor if you also want to use AdvantageKit's `@AutoLog` on
> your own classes. The bridge does not require it — that is the whole point of it.

### Step 3 — Install this Feature

Drop this repository's `MarsFeature.json` into your robot's `workspace-mars/features/`
directory (or install it from the MARS Terminal). The MARS robot `build.gradle` already scans
that directory and resolves `mavenUrls` and `javaDependencies` on its own — nothing else to
wire up.

```
your-robot/
├── vendordeps/
│   ├── AdvantageKit.json        <- step 1
│   ├── Mars.json
│   └── ForgeMini.json
└── workspace-mars/features/
    └── AdvantageKitBridge.json  <- step 3
```

### Step 4 — Switch `Robot` to `MarsLoggedRobot`

AdvantageKit **requires** the robot class to inherit from `LoggedRobot` instead of
`TimedRobot`. `MarsLoggedRobot` makes that change and performs the entire logger setup from
the MARS `RunMode`:

```java
public class Robot extends MarsLoggedRobot {

  private final IRobotContainer m_robotContainer;

  public Robot() {
    super(Manifest.CURRENT_MODE);   // Environment, receivers, Logger.start() and install()

    DriverStation.silenceJoystickConnectionWarning(true);
    m_robotContainer = new RobotContainer();
  }

  @Override
  public void robotPeriodic() {
    CommandScheduler.getInstance().run();
    m_robotContainer.updateNodes();
  }
}
```

That is it. Subsystems do not get registered one by one.

`LoggedRobot` has two differences from `TimedRobot` worth knowing: it does not support
`addPeriodic`, and `setUseTiming(false)` lets replay run as fast as the machine allows
(`MarsLoggedRobot` does that for you in `REPLAY`).

#### Extra receivers

Each run mode brings the receivers AdvantageKit's guide prescribes for it: `REAL` writes a
`.wpilog` and publishes to NetworkTables, `SIM` only publishes, `REPLAY` reads the selected
log and writes a `_sim` copy beside it. Note what `SIM` does *not* do — it records no file,
so desktop simulation produces nothing to replay later.

Anything beyond the mode's own receivers goes in the constructor, because `Logger.start()`
has already run by the time your constructor body executes and AdvantageKit rejects
receivers after that:

```java
public Robot() {
  super(Manifest.CURRENT_MODE, new WPILOGWriter("logs"));   // sim now records too
}
```

Extras are registered in every mode. To pick per mode, compute them in a **static** helper —
it is a `super(...)` argument, so it is evaluated before the instance exists:

```java
public Robot() {
  super(Manifest.CURRENT_MODE, simLogger(Manifest.CURRENT_MODE));
}

private static LogDataReceiver[] simLogger(RunMode mode) {
  return mode == RunMode.SIM
      ? new LogDataReceiver[] {new WPILOGWriter("logs")}
      : new LogDataReceiver[0];
}
```

The same parameter exists on the `BridgeConfig` and loop-period overloads:

```java
super(Manifest.CURRENT_MODE, config, new WPILOGWriter("logs"));
super(Manifest.CURRENT_MODE, config, 0.02, new WPILOGWriter("logs"));
```

A log recorded in `SIM` replays like any other — but it holds simulated hardware, so the
replay reproduces the simulation, not a match. For a real match, record in `REAL` and pull
the `.wpilog` off the USB stick.

### Requirements

| Component | Minimum version |
|---|---|
| MARS Core | `1.6.5` — the release that introduced `addGlobalInputHook` |
| ForgeMini | `1.1.2` |
| AdvantageKit vendordep | `26.0.2` |
| WPILib | `2026.1.1` |

### On a real robot

`WPILOGWriter` writes to a USB stick on the roboRIO. **A FAT32-formatted USB stick must be
plugged into one of the roboRIO's USB ports**, otherwise the log falls back to internal
storage and you will run out of space quickly.

---

## What ends up in the log

| Path | Kind | Contents |
|---|---|---|
| `/<Subsystem>/<Field>` | input | Every public field of the subsystem's `Data` |
| `/RealOutputs/Mars/Diagnostics/<Subsystem>/Code` | output | `StatusColorCode` name |
| `/RealOutputs/Mars/Diagnostics/<Subsystem>/Severity` | output | `OK` \| `WARNING` \| `ERROR` \| `CRITICAL` |
| `/RealOutputs/Mars/Diagnostics/<Subsystem>/Message` | output | The `ActionStatus` message |
| `/RealOutputs/Mars/Diagnostics/<Subsystem>/Timestamp` | output | FPGA timestamp of the status |
| `/RealOutputs/Mars/Diagnostics/<Subsystem>/ColorHex` | output | Evaluated LED colour, blink included |
| `/RealOutputs/Mars/Diagnostics/ActiveAlerts` | output | `String[]` of non-nominal subsystems |
| `/RealOutputs/Mars/Diagnostics/ActiveAlertCount` | output | How many alerts are active |
| `/RealOutputs/Mars/Diagnostics/HasCriticalAlert` | output | `true` if anything is `CRITICAL` |
| `/RealMetadata/MarsVersion` | metadata | MARS Core version |
| `/RealMetadata/MarsRunMode` | metadata | `REAL` \| `SIM` \| `REPLAY` |

Diagnostics are **outputs** on purpose: an `ActionStatus` is *derived* from the inputs. During
replay they are recomputed, so comparing `/RealOutputs` against `/ReplayOutputs` in
AdvantageScope shows you exactly where a logic change altered the robot's decisions.

The subsystem name is WPILib's (`SubsystemBase.getName()`), which defaults to the simple name
of the subsystem class.

---

## Supported types

Everything AdvantageKit knows how to represent:

| Category | Types |
|---|---|
| Primitives | `boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double` |
| Wrappers | `Boolean`, `Byte`, `Short`, `Character`, `Integer`, `Long`, `Float`, `Double` |
| Text and enums | `String`, any `enum` (including constants with bodies), `Enum[]` |
| 1D arrays | `byte[]` (raw), `boolean[]`, `int[]`, `long[]`, `float[]`, `double[]`, `String[]` |
| 2D arrays | the same ones, as `[][]` |
| Structs | `Pose2d`, `Rotation2d`, `ChassisSpeeds`, `SwerveModuleState`, ... and **any type of yours with a `public static final Struct<T> struct` field** |
| Struct arrays | `Pose2d[]`, `SwerveModuleState[]`, ... and 2D |
| Records | any `record` and its arrays |
| Units | `Measure<?>` (`Distance`, `Angle`, ...), with its base unit as metadata |
| Other | `Color` (hex), nested objects that already implement `LoggableInputs` (e.g. `@AutoLog` classes) |

A field that fits none of these is **skipped**, and reported once on the console. The rest of
the snapshot keeps logging: a robot missing one chart is recoverable, one that throws inside
`periodic()` is not.

> Careful: a skipped field is also not restored during replay, so if your logic reads it, the
> replay stops being deterministic. The warning says so explicitly.

### Optional annotations

```java
public class ArmInputs extends Data<ArmInputs> {

    @LogUnit("Degrees")          // unit metadata for AdvantageScope
    public double position = 0.0;

    @LogName("PositionRaw")      // custom log key
    public double rawPosition = 0.0;

    @LogExclude                  // neither logged nor replayed
    public double scratch = 0.0;
}
```

`@LogUnit` is the *runtime* counterpart of the `@Unit` annotation from the MARS UnitProcessor
Feature: that one is `RetentionPolicy.SOURCE`, so it cannot be read reflectively on the robot.

`transient` and `static` fields are always ignored.

---

## Replay

```
REAL   -> WPILOGWriter (USB / logs) + NT4Publisher
SIM    -> NT4Publisher
REPLAY -> WPILOGReader(chosen log) + WPILOGWriter(<log>_sim), no real-time pacing
```

To run a replay:

1. Set `CURRENT_MODE = RunMode.REPLAY` in your `Manifest`.
2. In the VS Code simulation dialog, **uncheck everything** (Sim GUI and DriverStation).
   AdvantageKit refuses to replay with HAL simulation extensions loaded.
3. Run the simulation and pick the `.wpilog`.
4. Open the resulting `_sim.wpilog` in AdvantageScope next to the original.

### Inert IO during replay (optional, recommended)

Replay is already correct without this, because the hook runs **after** `updateInputs`. What
this removes is the waste: MARS' `Injector` hands `REPLAY` the simulation IO, so a plain
replay keeps integrating physics models whose output is discarded a moment later — burning
exactly the CPU that makes replay run faster than real time.

```java
// before
ArmIO io = Injector.createIO(HAS_ARM, ArmIOFallback::new, ArmIOKraken::new, ArmIOSim::new);

// after
ArmIO io = MarsReplayIO.createIO(
    ArmIO.class, HAS_ARM, ArmIOFallback::new, ArmIOKraken::new, ArmIOSim::new);
```

In `REPLAY` it generates, through a `Proxy`, an implementation of your `IO` interface that
reads no hardware, drives no actuators, keeps your `default` methods, and — this is the
important part — reports `isFallback() == false`. MARS skips the entire `periodicLogic()` of a
fallback subsystem, and a skipped block never reaches the hook: an IO that reported itself as
a fallback would produce an empty replay, silently.

Limitation: only **interfaces** can be proxied. A `CompositeIO` (an abstract class) keeps the
simulation implementation; that is harmless, because a composite's children are replayed
through their own hooks.

---

## Configuration

```java
public class Robot extends MarsLoggedRobot {
  public Robot() {
    super(Manifest.CURRENT_MODE,
        BridgeConfig.builder()
            .keyStyle(BridgeConfig.KeyStyle.ADVANTAGEKIT) // or RAW
            .inputsPrefix("")            // "Mars/" to group under one node
            .logDiagnostics(true)
            .diagnosticsRoot("Mars/Diagnostics")
            .logStatusColor(true)
            .logNestedObjects(false)     // recurse into plain objects (opt-in)
            .maxNestingDepth(3)
            .build());
    ...
  }
}
```

| Option | Default | What it does |
|---|---|---|
| `keyStyle` | `ADVANTAGEKIT` | `position` → `Position`, same as the `@AutoLog` processor. `RAW` keeps the name as declared, the way ForgeMini publishes it |
| `inputsPrefix` | `""` | Prefix for the input tables. Empty = log root, same as a hand-written AdvantageKit project |
| `logDiagnostics` | `true` | Mirrors `AlertRegistry` into outputs |
| `logStatusColor` | `true` | Includes the evaluated LED pattern hex |
| `logNestedObjects` | `false` | Recurses into plain object fields. Off by default so one stray reference cannot drag half the robot into the log |

---

## Manual setup (without `MarsLoggedRobot`)

If you would rather drive AdvantageKit yourself, the order matters:

```java
public class Robot extends LoggedRobot {
  public Robot() {
    Environment.setMode(Manifest.CURRENT_MODE);   // 1. before building any subsystem
    AdvantageKitBridge.configure(BridgeConfig.defaults());

    Logger.recordMetadata("GitSHA", BuildConstants.GIT_COMMIT);
    AdvantageKitBridge.configureLogger(Manifest.CURRENT_MODE);  // 2. receivers / replay source
    if (Manifest.CURRENT_MODE == RunMode.REPLAY) setUseTiming(false);

    Logger.start();                               // 3. before the first subsystem exists
    AdvantageKitBridge.install();

    m_robotContainer = new RobotContainer();
  }
}
```

Structures MARS does not own can join the same replayable pipeline:

```java
AdvantageKitBridge.processInputs("Vision", visionSnapshot);
```

---

## How it interacts with the rest of the ecosystem

* **ForgeMini / NetworkIO** keeps working unchanged. AdvantageKit publishes under
  `/AdvantageKit/...` on NetworkTables, so there is no key collision with what ForgeMini
  publishes at the root.
* **`@AutoLog`** still works: if a `Data` class already implements `LoggableInputs` (for
  example a generated `XxxAutoLogged`), the bridge detects that and passes it straight to the
  logger with no reflection.
* **`@AutoLogOutput`** works as-is: `LoggedRobot` registers the robot itself, and
  `MarsLoggedRobot` inherits from it.
* **MARSWatchdog** and **GCSConsole** are untouched; they keep reporting through their own
  channel.

---

## Developing this repo

```bash
./gradlew build                              # compile + tests + javadoc
./gradlew test                               # 33 tests
./gradlew test -DmarsBridge.halTests=true    # plus the record tests (need a loadable HAL)
./gradlew publish                            # publishes to ./maven
```

The record tests are off by default. AdvantageKit derives a `record`'s struct lazily and, on
the way, asks the Driver Station whether the robot is enabled — which loads the HAL. On
machines where WPILib's desktop HAL fails to load, that takes down the whole JVM rather than
throwing something a test could catch. Records work normally on a robot, in simulation and in
replay, where the HAL is always present.

The GitHub Actions workflow publishes the Maven repo and the Javadoc to `gh-pages` on every
push to `main`.
