# STEMley competition-readiness review

## Verdict and scope

Prioritize validating the exact deployed program, shooter runtime settings, calibration, and autonomous startup. Do not replace the vision system or broadly retune controls on the eve of competition based only on source review. Concrete source-level faults exist, but a complete known-good rollback is incompatible with the removed climber and changed shooter.

Compared:
- Baseline explicitly selected by Nathan: `BCHRobotics/2026`, dev commit `bdd30c5c10f277124832eaf783120812bc7dbee3`.
- Current: `BCHRobotics/2026_STEMley`, dev commit `c276d06f9fa752bf5a3a41a91046cb8518043f36`.
- Current checkout: `/root/workspace/FRC Robots/2026_STEMley`.

Read-only source review and isolated compilation. No robot connection, robot-source modifications, deployment, pushes, or delegation. Excluded path geometry and route optimization at Nathan's request; checked selected-auto loading and named-command references because these affect runtime reliability independently of geometry. Remote refs were fetched; the working tree remained clean.

## Clarifications confirmed by Nathan

- Cameras and their physical positions/angles are unchanged.
- Retain current powered idle behaviour tomorrow, including the enabled idle after Stop/Kill.
- 30 A per flywheel motor has been deliberately tested. Do not revert to 60 A simply because an earlier branch used it.
- Intake calibration at 4 A has been deliberately tested.
- Newly selected autos intentionally keep shooting/feeding until disabled, including OSNRB_Auto and OSNRT_Auto during their final driving segment.

These are intentional behaviours or human-reported validation, not independent hardware validation by this review.

## Reliability findings, prioritized

All Java paths below are relative to `src/main/java/frc/robot/` in the current revision.

### R1 — High: persistent fixed-RPM/tuning state can override competition defaults

Locations: `subsystems/Shooter.java:110–133,396–434`; `Constants.java:329–341`.

New runtime tuning values are published with setDefaultNumber/setDefaultBoolean and marked persistent. This now includes `Shooter/Tuning/UseDistanceForTargetRPM` and `TestRPM`. If distance targeting was disabled during testing, rebooting or redeploying does not necessarily restore distance targeting; the saved setting wins. Saved gains, output ceiling, readiness, and feeder speed can also differ from Constants.java. There is no FMS/tuning-mode gate and no finite-number check or meaningful upper bound for test RPM/gains.

Match effect: a mechanically correct autonomous can shoot with a stale fixed target, disabled output ceiling, inappropriate readiness, or testing gains. This is a new operational risk relative to the baseline's nonpersistent old tuning namespace.

Tomorrow: inspect and record effective tuning values on the actual robot after a restart; verify distance mode and changing target RPM with distance. Freeze the tested configuration rather than rewriting tuning infrastructure tonight. Inspect actual values, not only source defaults.

### R2 — High: feeder readiness is weak for the new shooter targets

Locations: `subsystems/Shooter.java:247–252,357–369,410–412,452–456`; `Constants.java:329–330`.

Readiness remains `abs(actualRPM-targetRPM) <= abs(targetRPM-readyRPM)`, with default ReadyRPM 1470. New distance targets are 2300–2900 RPM instead of the baseline's 1500–3040 RPM over a different distance range. As a result, 1470 RPM can permit feeding at roughly 64% of the new minimum target or 51% of the maximum target. The formula is a window, not a percentage-of-target test, and only the leader encoder is used. The basic readiness formula is inherited; its interaction with the revised targets and drum inertia needs validation.

The normal start delay also changed from 0.5 to 1.5 seconds. This timer measures time since startShooter(), not stable time at speed. A four-second shooter-on interval has at most approximately 2.5 seconds available for feeding before accounting for speed losses and scheduler timing. Increasing the delay alone does not make readiness target-relative.

Tomorrow: verify actual leader RPM at first feeder movement and recovery during sustained feeding. If the tested behaviour produces reliable shots, preserve it for the event; if not, the smallest candidate fix is a validated target-relative readiness band, not more PathPlanner P. Any actuator-behaviour change requires a physical retest.

### R3 — Medium: chooser contains an auto whose file is missing

Location: `RobotContainer.java:143`.

`IntakeTest_Auto` is constructed and published, but `src/main/deploy/pathplanner/autos/IntakeTest_Auto.auto` is absent. In pinned PathPlanner 2026.1.2, FileNotFoundException is caught, reported to Driver Station, and the auto becomes `Commands.none()`; this is not a whole-robot crash. Selecting it therefore produces no path execution after the calibration wrapper.

The exact baseline had a different missing selectable filename (`NRNBSCLRT_Auto `, with a trailing space); that historical defect is no longer the current one. Static audit found no missing path references or unregistered named commands in the other currently selectable autos and their referenced path events.

Tomorrow: do not select IntakeTest_Auto. Removing the stale chooser entry is a small candidate cleanup, but source was not edited. Always explicitly select and verify the desired auto after robot/dashboard restart; the chooser has no explicit default option, an inherited condition.

### R4 — Medium: calibration failure prints a message but still starts the auto

Locations: `RobotContainer.java:416–422`; `commands/ballintake/CalibrateBallIntakeCommand.java:15–23`; `subsystems/BallIntake.java:115–129,374–387`.

The sequential wrapper calibrates the intake, prints a failure message if unsuccessful, and then unconditionally starts selectedAuto. Position-controlled extend/retract commands refuse motion when not calibrated, while `run()` can still start the roller. Thus the robot can drive its auto with a nonfunctional/stowed intake. This control-flow defect is inherited, but remains consequential with an autonomous routine dependent on the hopper/intake.

Calibration is restarted on every autonomous start, even if already calibrated; it can consume up to the configured three seconds. CalibrateBallIntakeCommand has no end(interrupted) cleanup; an interruption during homing also deserves a disable/re-enable test. Physical controller output while disabled is inhibited, but calibration state/timing continues in subsystem periodic.

Tomorrow: make calibration success a preflight check and observe an actual auto start. Consider an abort-on-failure gate only with a small reviewed change and a failure-case retest. Do not silently remove homing or guess a new threshold; Nathan confirms the new 4 A threshold is tested.

### R5 — Medium: drive behaviour changed substantially beyond the mechanical changes

Locations: `Configs.java:20,31–34`; `RobotContainer.java:274–281`; `Constants.java:62–64,183,230`.

- Drive feedforward changed from `1/freeSpeed` to `12/freeSpeed`. Both baseline and current use REVLib 2026.0.1 and the same voltage-based API. This is a numerical control change, not a library upgrade in this comparison.
- All teleop joystick 0.6 multipliers were removed, including rotation.
- Normal max speed changed 4 -> 5 m/s; turbo 5.5 -> 5 m/s.
- With the unchanged deadband, full straight forward teleop request increases from approximately 2.32 to 5 m/s in normal mode. This is commanded speed, not a measured performance claim.
- Translation PathPlanner gains changed (P,I,D) from (2,0.1,0) to (1,0.1,0); rotation remains (1,0,0).
- The drive motor feedback actually applied remains hardcoded (0.04,0,0) in Configs.java, not the differently valued unused ModuleConstants gains.

Tomorrow: verify measured wheel/chassis speeds and battery voltage with the tested feedforward before further outer-loop PID changes. Inform drivers that rotation sensitivity and full-stick speed increased. Do not assume previously proposed changes are still missing; dev already contains the feedforward and joystick changes.

### R6 — Medium: physical model is still inconsistent with drivetrain configuration

Locations: `src/main/deploy/pathplanner/settings.json:54–70`; `Constants.java:72–73,178–191`.

Current PathPlanner model: 0.042 m wheel radius (~3.31 in diameter), gearing 5.143, Kraken X60, module X/Y magnitudes 0.273 m, mass 74.088 kg, MOI 6.883. Java drivetrain: ~2.98 in wheel diameter, reduction 5.5, module half-spacing 0.32385 m (25.5 in wheelbase/track). Nathan confirms nominal physical wheel diameter is 3 in. PathPlanner radius for nominal 3 in diameter is 0.0381 m.

Only wheel radius changed in the robot-model physical parameters relative to the baseline (0.048 -> 0.042 m); the other mismatches predate the mechanical changes. Do not treat old success as proof those parameters match the rebuilt robot. Removed climber/new drum may change mass and inertia; these need measurement, not invented replacements. Output callback ignores PathPlanner drive feedforwards, but robot-model parameters still influence generated trajectory feasibility.

Tomorrow: verify model against actual hardware and prioritize correctly measured wheel size. Retest regenerated trajectory timing after any model change. This review does not recommend wholesale model/route rewrites the night before competition.

### R7 — Inherited: vision/odometry weaknesses remain, but are not a new vision implementation regression

Locations: `Vision.java:186–209,287–324,345–421`; `Constants.java:146–159`; `subsystems/Drivetrain.java:181–188,220–239,326–327,452–455`.

Vision.java is byte-for-byte identical to the selected baseline. Camera names, transforms, uncertainty values and broad multi-tag gates are unchanged. Camera count 4 -> 2 is array/documentation cleanup: the baseline loop was already bounded by its two-element enable array, so both revisions initialize two cameras. Maximum accepted distance changed 8 -> 7 m, which may reduce corrections near the cutoff, but is not a new filtering algorithm.

Existing concerns include camera heading corrections carried through vision outages, a 99 m multi-tag translation gate and ineffective 300 degree wrapped-heading gate, current-time rather than capture-time residual checking, camera-order fusion, treating multiple detected targets as a successful multi-tag solve, and newest-frame-only processing. The estimator continues wheel/gyro propagation when vision is unavailable; there is no HasVisionPose-driven fallback/reset.

Gyro implementation now hardcodes negative NavX angle. Baseline default Flip Gyro=true also returned negative angle; the default sign is equivalent. The dashboard toggle was removed. Nathan's intervening OmniMount reset is an external hardware-configuration change, and his inversion test resolved the opposite-rotation symptom.

Tomorrow: validate raw wheel/gyro odometry with cameras obscured and observe reacquisition; ensure both coprocessors and robot use the same actual event AprilTag layout. Source loads Andymark, but a mismatch on the physical coprocessor is not proven by this review. Avoid rewriting vision fusion or adding new fallback modes without physical validation time.

### R8 — Inherited: PID chooser lifecycle is misleading

Locations: `RobotContainer.java:157–170,400–422`; `subsystems/Drivetrain.java:164–197`.

Autos are constructed with the startup controller, then AutoBuilder is configured again at autonomous start. Existing path commands keep the controller captured at construction. PID choosers are not published; their labels also disagree with actual defaults. Source constants therefore govern the startup-built commands, and source constant changes do take effect on rebuild/deploy/restart. This arrangement is inherited, not introduced by climber removal.

Tomorrow: use verified source constants and avoid relying on chooser-based retuning. A lifecycle cleanup is a later task unless already physically tested.

## Intentional changes identified

| Area | Baseline -> current | Classification |
|---|---|---|
| Climber | Subsystem, commands, constants, chooser, controls, named climber command and auto/teleop calibration removed | Matches mechanical removal |
| Shooter hardware | CAN 22 leader + CAN 23 follower -> CAN 22 leader + CAN 23/24/25 followers | Matches four-motor drum |
| Shooter inversion | Leader inverted; 23 inverted relative to leader; new 24 non-inverted and 25 inverted relative to leader | Physical directions must agree; source comment incorrectly says all followers inverted |
| Shooter limit | No explicit flywheel limit -> 30 A on leader and every follower | Nathan confirms tested; preserve |
| Shooter RPM curve | 1500–3040 RPM over 1–3 m -> 2300–2900 RPM over 1–3.81 m | Changed mechanism calibration; verify shooting results rather than restoring old curve |
| Feeder delay | 0.5 -> 1.5 seconds from shoot request | Changed autonomous shooting time budget |
| Shooter tuning | Old nonpersistent keys -> persistent Shooter/Tuning namespace and distance/TestRPM selector | Operational preflight required |
| Enabled idle | 1500 RPM idle retained; Stop/Kill returns to powered idle on next periodic update | Nathan explicitly accepts for tomorrow; labels/comments misleading |
| Disable/reverse | Disable clears latched shooter/feed/reverse state; reverse explicitly stops flywheel | Improvement relative to baseline behaviour |
| Intake homing | 20 -> 4 A filtered-current threshold; same 20-sample filter, speed, timeout | Nathan confirms tested |
| Driver controls | Intake toggle moves Circle/B -> L1/left bumper | Driver briefing required |
| Named commands | Adds intake retract; reverse on becomes timed 3.5 s; removes reverse off and climber on | No missing named references found in selected routines |
| Intake retract | Retracts arm without stopping roller or shooter/feeder | Confirmed intentional keep-shooting-to-disable behaviour |
| Gyro | Dashboard-selected inversion -> hardcoded negative angle | Same as baseline default sign |
| Wheel diameter | 3 -> ~2.98 in in Java; PathPlanner ~3.78 -> ~3.31 in diameter | Model discrepancy remains |
| Cameras | Nominal count 4 -> 2; cutoff 8 -> 7 m | Still two active cameras; mounts unchanged per Nathan |
| Dependencies | Gradle build files, wrapper and all vendordeps identical | No dependency upgrade to blame in this comparison |

Robot.java, Vision.java, BallIntake.java, MAXSwerveModule.java and all surviving command classes are unchanged. Java changes are concentrated in Configs, Constants, Main (comments only), RobotContainer, Drivetrain, Shooter, and deletion of three climber-related files. Path and auto asset edits were not assessed for route correctness.

## Verification performed

| Check | Result / limitation |
|---|---|
| Baseline revision | Cloned exact approved dev SHA and verified |
| Current revision | HEAD matches freshly fetched origin/dev; clean working tree |
| File comparison | Hash/diff comparison; unchanged systems/dependencies identified programmatically |
| Baseline `./gradlew --no-daemon --max-workers=1 compileJava test` | Successful in isolated non-root sandbox; climber deprecation warning; `test NO-SOURCE` |
| Current same Gradle command | Successful in isolated non-root sandbox; `test NO-SOURCE` |
| Offline attempts | Initially failed because private cache lacked GradleRIO artifacts; ordinary online dependency resolution succeeded, no tool installation |
| Current `python3 utilities/test_shooter_stop.py` | Fails: `Stop still commands an idle velocity`. Method-level fake IO, not REV hardware. Expected conflict with Nathan's explicitly accepted powered-idle behaviour; not silently fixed |
| Chooser/reference audit | 20 selectable PathPlannerAuto constructors; one missing auto file (IntakeTest_Auto); other selected routines' referenced paths and named commands resolved |
| Physical robot / deployed SHA / controller firmware / NT saved values | Not verified |
| Route geometry / full match duration / FMS / field-layout matching | Not verified; geometry excluded |

## Minimum competition preflight

1. Record the exact deployed commit, verify DS errors, explicitly choose a known routine after restart, and avoid missing IntakeTest_Auto.
2. Verify actual persistent shooter settings after reboot: distance selector, TestRPM, PIDF, output cap, ReadyRPM and feeder speed.
3. Confirm intake homes from both ends with tested 4 A threshold; observe calibration failure/recovery and auto start delay.
4. Confirm all four drum motors help rather than fight and monitor individual currents/temperatures. Human-reported 30 A testing does not substitute for checking all motors.
5. Observe measured RPM when the feeder first starts, sustained-shot recovery, and the increased 1.5 s delay's effect on available feeding time.
6. Confirm disable removes power, document enabled powered idle and brake/kill behaviour, and keep people clear whenever enabled.
7. Test raw odometry with vision obscured, then reacquisition, with gyro direction already verified; validate field layout at the venue.
8. Verify actual drive speed, battery voltage, driver control changes, and stop behaviour. Do not make blind PathPlanner P increases to compensate for a lower-level issue.

No conclusion here is a hardware acceptance sign-off. Make only small, reviewed fixes with time to rerun the affected physical sequence; preserve a known tested build and avoid broad dependency, vision, or architecture changes before competition.
