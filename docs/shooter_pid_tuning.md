# Shooter PID Tuning (Disposable Branch)

Live tuning values for the shooter flywheel are published to SmartDashboard, which
Elastic reads. Changes apply on the next robot loop without a redeploy.

## Dashboard Keys

All tuning keys live under `Shooter/Tuning/`.

| Key | Type | Default | Notes |
|-----|------|---------|-------|
| `P` | double | `0.003` | Velocity proportional gain |
| `I` | double | `0.0` | Velocity integral gain |
| `D` | double | `0.0` | Velocity derivative gain |
| `F` | double | `0.000162` | Velocity feedforward (`kV`) |
| `MaxOutput` | double | `0.85` | Closed-loop output limit, clamped to [0, 1] |
| `ReadyRPM` | double | `target * 0.98` | RPM tolerance before the feeder runs |
| `FeederSpeed` | double | `1.0` | Feeder duty cycle, clamped to [0, 1] |
| `TestRPM` | double | `1500` | Fixed setpoint used for PID tuning |
| `UseDistanceForTargetRPM` | boolean | `true` | When false, uses `TestRPM` instead of hub-distance interpolation |
| `Run Flywheel` | command | - | Spins the flywheel at the active setpoint (no feeding) |
| `Stop Flywheel` | command | - | Removes flywheel and feeder power; flywheel coasts down |

PIDF and `MaxOutput` changes are pushed to the SPARK Flex closed-loop config only
when a value changes, using `kNoPersistParameters` to avoid flash wear. Gains are
not persisted on the motor controller.

## Motor configuration and stop behaviour

This tuning tree incorporates commit `6b69fe3` from
`feature/single-shooter-four-vortex`: CAN 22 leads three inverted followers on
CAN 23/24/25, with the feeder on CAN 21. Verify physical directions before use.
Stop clears all shooter/feeder requests and removes motor power; there is no
powered idle RPM. Disable also clears the latched dashboard run request, so
re-enable alone does not resume it. A new shoot command can start it again.
The spinning flywheel still takes time to coast down.

## Tuning Workflow

1. Deploy this branch and open Elastic.
2. Set `UseDistanceForTargetRPM` to `false` and pick a `TestRPM` for the shot
   you are tuning (for example, the RPM the distance model produces at your
   practice distance).
3. Tune `F` first until the flywheel settles near the setpoint with minimal
   output, then `P`, then `D`, then `I`.
4. Use `Run Flywheel` / `Stop Flywheel` to blame-check gains without holding a
   controller button. `Shooter/Shooter1RPM`, `Shooter/Shooter2RPM`,
   `Shooter/RPMError`, and the current keys are published for plotting.
5. Once the shot is dialed in, return `UseDistanceForTargetRPM` to `true` and
   verify the distance-based RPM at a few field positions.
6. Copy the final values into `ShooterConstants` in `Constants.java`, then delete
   this branch.

Tuning keys are persistent NetworkTables entries, so the last values typed into
Elastic survive a roboRIO restart. They are not a substitute for committing the
final gains to code.
