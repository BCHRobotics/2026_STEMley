# Shuffleboard / SmartDashboard Elements

This file contains a list of all elements currently being published to Shuffleboard/SmartDashboard.
Please edit this file to indicate which items should be kept or removed.

## RobotContainer
- [ ] `Auto Mode` (SendableChooser)
- [ ] `Field Pose` (SendableChooser)
- [R] `PP Translation PID` (SendableChooser)
- [R] `PP Rotation PID` (SendableChooser)
- [ ] `Autonomous Mode` (SendableChooser) - *Note: Appears to be a duplicate of "Auto Mode"*

## Drivetrain
- [ ] `Gyro Heading` (Number)

## Ball Intake
- [R] `Ball Intake/Speed` (Number)
- [R] `Ball Intake/Current` (Number)
- [R] `Ball Intake/Running` (Boolean)

## Vision
- [ ] `Vision Field` (Field2d)
- [R] `Vision/Total Cameras` (Number)
- [R] `Vision/Cameras With Targets` (Number)
- [R] `Vision/Total Targets` (Number)

### Per Camera (Vision/Cam{index}/...)
- [R] `.../Has Targets` (Boolean)
- [R] `.../Target Count` (Number)
- [R] `.../Timestamp` (Number)
- [R] `.../Name` (String)
- [R] `.../Best Target ID` (Number)
- [R] `.../Ambiguity` (Number)
- [R] `.../Area` (Number)
- [R] `.../Alliance` (String)

## Vision WebServer
- [R] `WebServer/Running` (Boolean)
- [R] `WebServer/Port` (Number)

## Shooter (tuning)
- [R] `Shooter/Distance` (Number)
- [R] `Shooter/TargetRPM` (Number)
- [R] `Shooter/ReadyRPM` (Number)
- [R] `Shooter/Shooter1RPM` (Number)
- [R] `Shooter/Shooter2RPM` (Number)
- [R] `Shooter/AppliedFeederSpeed` (Number)
- [R] `Shooter/RPM` (Number)
- [R] `Shooter/Charged` (Boolean)
- [R] `Shooter/Active` (Boolean)
- [R] `Shooter/VortexSpeedShotActive` (Boolean)
- [R] `Shooter/VortexSpeedShotReady` (Boolean)

### Shooter Tuning Inputs
- [R] `Shooter/Tuning/P` (Number)
- [R] `Shooter/Tuning/I` (Number)
- [R] `Shooter/Tuning/D` (Number)
- [R] `Shooter/Tuning/F` (Number)
- [R] `Shooter/Tuning/MaxOutput` (Number)
- [R] `Shooter/Tuning/ReadyRPM` (Number)
- [R] `Shooter/Tuning/FeederSpeed` (Number)
- [R] `Shooter/Tuning/TestRPM` (Number)
- [R] `Shooter/Tuning/UseDistanceForTargetRPM` (Boolean)
- [R] `Shooter/Tuning/Run Flywheel` (Command)
- [R] `Shooter/Tuning/Stop Flywheel` (Command)
