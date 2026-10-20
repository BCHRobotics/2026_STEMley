# Dedicated Elastic shooter tuning dashboard

## Import

Use **Elastic v2026.1.2** (the release tested for this layout) and
[`shooter-tuning.json`](shooter-tuning.json). It uses the native layout format,
not a Shuffleboard export, Kibana dashboard, or AdvantageScope configuration.

1. Back up your existing layout with **File → Save As** (called Export Layout
   in the documentation). Opening this file replaces the current dashboard tabs.
2. If necessary unlock the layout under **Edit**.
3. Choose **File → Open Layout** or **Ctrl+O** and select `shooter-tuning.json`.
4. Confirm **Shooter Tuning** and **Motor Health** appear. Use **Ctrl+S** to save.
5. For live use, connect Elastic to the robot through the usual Driver Station /
   team **2386** connection settings. A layout file does not set the connection.
6. Lock the layout under **Edit → Lock Layout** before using controls.

Designed for a 1280 × 608 content area (a roughly 1280 × 720 or larger window;
OS title bars/scaling may require more room). UI verification used 1440 × 800.

## Contents

| Tab | Widgets | Purpose |
|---|---:|---|
| Shooter Tuning | 18 | Run/Stop, shoot-request/readiness lamps, actual and target RPM, P/I/D/F, max output, ready threshold, feeder speed, test RPM, distance/fixed selection, and three 10-second plots |
| Motor Health | 18 | Stop, four signed motor RPM readouts and plots, four shooter currents plus feeder current, hub distance, ready threshold, feeder request and Vortex-mode status |

Numeric tuning fields have an explicit submit arrow. Enter a complete valid
number, then submit; merely importing the layout does not write default gains.
The target toggle means **ON = distance-based target**, **OFF = fixed TestRPM**.
All widgets request a 0.05-second NT subscription period; actual update rate
also depends on robot publishing and the connection.

The Run/Stop widgets use the native **Command** widget, bound to the command
sendable's parent path (not directly to `/running`). These are one-shot
commands: their button state is not a flywheel-running indicator. Watch
**Shoot request** and actual RPM instead. The Run command does not request
feeding. Stop removes motor power; the flywheel still takes time to coast down.
Stop is available on both tabs. Driver Station disable remains the primary
safety control, not a dashboard button or a connection-dependent status lamp.

## Topic compatibility

This layout targets `shooter-pid-tuning` at **2927bda**, which contains the
four-motor inverted-follower configuration and the stop fix. It requires that
robot code to have been deployed separately with mentor authorization.

- Tuning inputs: `/SmartDashboard/Shooter/Tuning/*`
- Run/Stop sendables: `/SmartDashboard/Shooter/Tuning/Run Flywheel` and
  `/SmartDashboard/Shooter/Tuning/Stop Flywheel`
- RPM, target, readiness, distance and state: `/SmartDashboard/Shooter/*`
- RPM error and current: `/AdvantageKit/RealOutputs/Shooter/*`

The last group uses `Logger.recordOutput`, not SmartDashboard. The installed
AdvantageKit **26.0.2** JAR was inspected to verify the `/AdvantageKit` publisher
root and `RealOutputs` subtable. `Robot.java` registers `NT4Publisher`.
Replay output paths are intentionally not used.

Follower RPM is plotted with its original sign; inverted followers may report
negative velocity. Graph axes autoscale rather than hiding negative readings.
**Feeder request (not output)** displays the existing `AppliedFeederSpeed`
telemetry, which is a requested speed, not the final readiness-gated motor output.

## Safe tuning limits and known code limitations

- Clear and restrain the mechanism, unload balls and verify all four motor
  directions/current sharing before any powered test.
- Start with a conservative positive TestRPM and keep max output at or below
  **0.85** until the gains are stable. Tune feedforward before feedback.
- Do not enter NaN, infinity, or excessive RPM/gains. The current robot code
  does not provide complete finite-value validation, gain/RPM upper bounds or
  an FMS/tuning-mode gate. Layout controls do not add those robot-side protections.
- Tuning entries are persistent NetworkTables values: old values can override
  compiled defaults after restart. Review them before enabling; save final gains
  in code rather than relying on the dashboard.
- Normal-shot feeder speed is captured when feeding starts; changing its tuning
  value during an existing normal shot does not immediately update that request.
- Blank/disabled widgets are expected while disconnected or before the matching
  robot topics have been published. Import success is not hardware validation.

## Verification performed

- Official release: **v2026.1.2**, source tag commit
  `3673f6b4135cbc429bb1f43e6ab109378ab2de6d`.
- Inspected the release's native layout loader, widget registry, Command,
  Text Display, Graph and common widget serialization code.
- Opened the unmodified official **Elastic-Web.zip** release locally and used
  its actual **Open Layout** file input to import the JSON.
- Viewed both tabs: all 36 widgets rendered with recognized widget types and no
  import warning/error dialog. No robot connection was present.
- Saved through Elastic's normal **Ctrl+S** handler and compared the parsed
  saved layout to the final input: exact match, including version, topics,
  geometry, types and properties. Save/reload also retained both tabs.
- Static validator checks native structure, explicit types, current source topic
  bindings, command paths, all tuning inputs, all motor RPM/current bindings,
  widget counts and non-overlapping in-bounds geometry.

Re-run from the repository root:

```bash
python3 utilities/validate_shooter_dashboard.py
```

This is an actual **Elastic web-release import/save test** plus source-based
compatibility checks, not a test on Nathan's desktop installation or a powered
robot. Desktop and web share the checked native JSON loader/widget models.
Older Elastic releases are not claimed tested. No robot Java changes, deployment,
repository commit or push were performed for this dashboard task.

## Official references

- [WPILib: Elastic overview and launch instructions](https://docs.wpilib.org/en/stable/docs/software/dashboards/elastic.html)
- [Elastic: App Navigation / Open Layout](https://frc-elastic.gitbook.io/docs/getting-started/app-navigation)
- [Elastic: Widgets and properties](https://frc-elastic.gitbook.io/docs/additional-features-and-references/widgets-list-and-properties-reference)
- [Elastic v2026.1.2 release](https://github.com/Gold872/elastic_dashboard/releases/tag/v2026.1.2)
- [Pinned layout importer source](https://github.com/Gold872/elastic_dashboard/blob/v2026.1.2/lib/pages/dashboard/dashboard_page_layouts.dart)
