package frc.robot;

import frc.robot.commands.drivetrain.FacePointCommand;
import frc.robot.commands.drivetrain.GoToPositionCommand;
import frc.robot.commands.drivetrain.GoToPositionRelativeCommand;
import frc.robot.commands.drivetrain.TeleopDriveCommand;
import frc.robot.commands.vision.AlignToAprilTagCommand;
import frc.robot.subsystems.BallIntake;
import frc.robot.subsystems.Drivetrain;
import frc.robot.subsystems.LED;
import frc.robot.subsystems.Vision;
import frc.robot.webserver.VisionWebServer;
import frc.robot.Constants.AutoConstants;
import frc.robot.Constants.LEDConstants;
import frc.robot.Constants.OIConstants;
import frc.utils.HubSchedule;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;

import com.pathplanner.lib.commands.PathPlannerAuto;
import com.pathplanner.lib.config.PIDConstants;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj.DataLogManager;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.CommandPS5Controller;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.Trigger;

public class RobotContainer {

    // ── Subsystems ────────────────────────────────────────────────────────────
    private final Drivetrain  robotDrive = new Drivetrain();
    private final Vision      vision     = new Vision(robotDrive);
    private final LED         led        = new LED();

    /**
     * Ball intake subsystem.
     *
     * <p>NOTE: {@link Constants.BallIntakeConstants} intentionally sets CAN ID = 99
     * (invalid) and all speeds to 0.0 while the physical hardware does not exist.
     * The LED logic is fully wired; it will activate the moment real constants are
     * supplied.  Button bindings are left commented out below.
     */
    private final BallIntake  ballIntake = new BallIntake();

    /** Vision diagnostics HTTP server on RoboRIO port 8082. */
    private final VisionWebServer webServer = new VisionWebServer(vision);

    // ── Controllers ───────────────────────────────────────────────────────────
    private CommandPS5Controller  driverPS5;
    private CommandXboxController driverXbox;

    // ── Choosers ──────────────────────────────────────────────────────────────
    private final SendableChooser<Command>      autoChooser             = new SendableChooser<>();
    private final SendableChooser<Pose2d>       fieldPoseChooser        = new SendableChooser<>();
    private final SendableChooser<PIDConstants> ppTranslationPidChooser = new SendableChooser<>();
    private final SendableChooser<PIDConstants> ppRotationPidChooser    = new SendableChooser<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public RobotContainer() {
        // Initialise the correct controller type.
        if (OIConstants.kDriverControllerType == OIConstants.ControllerType.PS5) {
            driverPS5  = new CommandPS5Controller(OIConstants.kMainControllerPort);
        } else {
            driverXbox = new CommandXboxController(OIConstants.kMainControllerPort);
        }

        // Start AdvantageScope / WPILib data logging.
        DataLogManager.start();

        // Give the drivetrain a reference to vision for console diagnostics.
        robotDrive.setVision(vision);

        // Start the HTTP vision diagnostics server.
        webServer.start();

        configureDefaultCommands();
        configureBindings();
        configureFieldPoseChooser();
        configureAutoChooser();
    }

    // ── LED State Machine ─────────────────────────────────────────────────────

    /**
     * Updates the LED strip to reflect the current robot state.
     *
     * <p>Call once per {@code robotPeriodic()} loop, AFTER
     * {@code CommandScheduler.getInstance().run()} so command and vision state is current.
     *
     * <p><b>Priority (first match wins):</b>
     * <ol>
     *   <li>DEPLOY            — first 2 s after boot</li>
     *   <li>GYRO_FAULT        — NavX disconnected (all modes)</li>
     *   <li>(disabled)        — strip off</li>
     *   <li>AUTO              — autonomous</li>
     *   <li>TEST              — test mode</li>
     *   <li>MATCH_END         — ≤ 10 s left</li>
     *   <li>ENDGAME           — ≤ 30 s left</li>
     *   <li>SHIFT_ON/OFF_WARNING — our hub opens/closes within kShiftWarningSeconds</li>
     *   <li>POSE_VALID        — vision lock (distance gradient)</li>
     *   <li>INTAKE / SHOOTER  — mechanisms running (both → purple edges, orange middle)</li>
     *   <li>TELEOP            — default</li>
     * </ol>
     */
    public void updateLEDState() {
        led.setOutputEnabled(true);

        // 1. Deploy confirmation.
        if (led.isDeployAnimationPlaying()) {
            led.setState(LED.State.DEPLOY);
            return;
        }

        // 2. Gyro fault — visible in every mode, including disabled (pit check).
        if (!robotDrive.gyro.isConnected()) {
            led.setState(LED.State.GYRO_FAULT);
            return;
        }

        // 3. Disabled → strip off (there is no DISABLED state).
        if (DriverStation.isDisabled()) {
            led.setOutputEnabled(false);
            return;
        }

        // 4. Mode.
        if (DriverStation.isAutonomous()) { led.setState(LED.State.AUTO); return; }
        if (DriverStation.isTest())       { led.setState(LED.State.TEST); return; }

        // 5. Teleop timing. getMatchTime() counts down from 140; < 0 means no timer.
        double t = DriverStation.getMatchTime();
        if (t >= 0 && t <= 10.0) { led.setState(LED.State.MATCH_END); return; }
        if (t >= 0 && t <= 30.0) { led.setState(LED.State.ENDGAME);   return; }

        // 6. Hub shift warning (needs match time + game data 'R'/'B' from the field).
        if (t >= 0) {
            boolean isRed = DriverStation.getAlliance()
                    .map(a -> a == DriverStation.Alliance.Red)
                    .orElse(false);
            HubSchedule.Status hub = HubSchedule.compute(
                    t,
                    DriverStation.getGameSpecificMessage(),  // alliance that goes inactive FIRST
                    isRed,
                    LEDConstants.kShiftWarningSeconds);

            if (hub.known() && hub.changingSoon()) {
                // Currently active → about to close; currently inactive → about to open.
                led.setState(hub.active() ? LED.State.SHIFT_OFF_WARNING
                                          : LED.State.SHIFT_ON_WARNING);
                return;
            }
        }

        // 7. Vision lock (distance expires automatically if we stop calling this).
        if (vision.hasValidPose()) {
            led.setDistanceToTarget(getNearestTagDistance());
            led.setState(LED.State.POSE_VALID);
            return;
        }

        // 8. Intake / shooter (both → purple edges + orange middle, handled in LED).
        boolean intakeRunning  = ballIntake.isRunning();
        boolean shooterRunning = false;  // TODO: shooter.isRunning() once the subsystem exists
        if (intakeRunning || shooterRunning) {
            led.setMechanisms(intakeRunning, shooterRunning);
            led.setState(intakeRunning ? LED.State.INTAKE : LED.State.SHOOTER);
            return;
        }

        // 9. Default.
        led.setState(LED.State.TELEOP);
    }

    /**
     * Returns the distance in metres from the robot's current pose to the nearest
     * visible AprilTag, or {@code -1} if no tags are available.
     *
     * <p>Pulls distances from {@link Vision#getDetailedAprilTagInfo()}, which
     * calculates them from the robot's pose-estimator position vs. each tag's known
     * position in the field layout.
     *
     * @return distance in metres to the nearest visible tag, or {@code -1}
     */
    private double getNearestTagDistance() {
        List<Vision.AprilTagInfo> tags = vision.getDetailedAprilTagInfo();
        if (tags == null || tags.isEmpty()) {
            return -1.0;
        }

        double nearest = Double.MAX_VALUE;
        for (Vision.AprilTagInfo tag : tags) {
            if (tag.distance >= 0 && tag.distance < nearest) {
                nearest = tag.distance;
            }
        }
        return nearest == Double.MAX_VALUE ? -1.0 : nearest;
    }

    // ── Chooser configuration ─────────────────────────────────────────────────

    /**
     * Populates the field-pose chooser on SmartDashboard.
     *
     * <p>Coordinates are blue-alliance-relative.
     * {@link GoToPositionRelativeCommand} mirrors them automatically when
     * the Driver Station reports red alliance.
     */
    private void configureFieldPoseChooser() {
        fieldPoseChooser.setDefaultOption("Climber 1",
                new Pose2d(1.037, 2.800, Rotation2d.fromDegrees(-90.0)));
        fieldPoseChooser.addOption("Climber 2",
                new Pose2d(1.061, 4.600, Rotation2d.fromDegrees( 90.0)));
        fieldPoseChooser.addOption("Climber 3",
                new Pose2d(1.037, 2.800, Rotation2d.fromDegrees(-90.0)));
        fieldPoseChooser.addOption("Climber 4",
                new Pose2d(1.061, 4.600, Rotation2d.fromDegrees( 90.0)));
        SmartDashboard.putData("Field Pose", fieldPoseChooser);
    }

    /**
     * Populates PathPlanner PID preset choosers on SmartDashboard.
     *
     * <p>Not called by default — call from the constructor if you want to expose
     * PID tuning to drivers at competition.
     */
    @SuppressWarnings("unused")
    private void configurePathPlannerPidChoosers() {
        ppTranslationPidChooser.setDefaultOption(
                "Default (2.0, 0.1, 0.0)", AutoConstants.translationConstants);
        ppTranslationPidChooser.addOption("Soft (1.2, 0.0, 0.0)",
                new PIDConstants(1.2, 0.0, 0.0));
        ppTranslationPidChooser.addOption("Aggressive (3.0, 0.0, 0.0)",
                new PIDConstants(3.0, 0.0, 0.0));

        ppRotationPidChooser.setDefaultOption(
                "Default (1.0, 0.0, 0.0)", AutoConstants.rotationConstants);
        ppRotationPidChooser.addOption("Soft (0.6, 0.0, 0.0)",
                new PIDConstants(0.6, 0.0, 0.0));
        ppRotationPidChooser.addOption("Aggressive (1.6, 0.0, 0.0)",
                new PIDConstants(1.6, 0.0, 0.0));

        SmartDashboard.putData("PP Translation PID", ppTranslationPidChooser);
        SmartDashboard.putData("PP Rotation PID",    ppRotationPidChooser);
    }

    /** Registers all PathPlanner auto routines with the chooser. */
    private void configureAutoChooser() {
        // First entry is setDefaultOption so getSelected() is never null
        // (safe to use without null-check, though getAutonomousCommand() still guards it).
        autoChooser.setDefaultOption("Square Auto",        new PathPlannerAuto("Square Auto"));
        autoChooser.addOption("Tuning_auto",               new PathPlannerAuto("Tuning_auto"));
        autoChooser.addOption("Circle Auto",               new PathPlannerAuto("Circle Auto"));
        autoChooser.addOption("Test Climber Centre",       new PathPlannerAuto("Test Climber Centre"));
        autoChooser.addOption("Climber-1_Auto",            new PathPlannerAuto("Climber-1_Auto"));
        autoChooser.addOption("Climber-2_Auto",            new PathPlannerAuto("Climber-2_Auto"));
        autoChooser.addOption("Climber-3_Auto",            new PathPlannerAuto("Climber-3_Auto"));
        autoChooser.addOption("Climber-4_Auto",            new PathPlannerAuto("Climber-4_Auto"));
        autoChooser.addOption("Climber-5_Auto",            new PathPlannerAuto("Climber-5_Auto"));
        autoChooser.addOption("Climber-6_Auto",            new PathPlannerAuto("Climber-6_Auto"));
        autoChooser.addOption("N_Climber-7_Auto",          new PathPlannerAuto("N_Climber-7_Auto"));
        autoChooser.addOption("Climber-8_Auto",            new PathPlannerAuto("Climber-8_Auto"));
        SmartDashboard.putData("Auto Mode", autoChooser);
    }

    // ── Default commands ──────────────────────────────────────────────────────

    private void configureDefaultCommands() {
        DoubleSupplier leftY, leftX, rightX;

        if (driverPS5 != null) {
            leftY  = () -> -driverPS5.getLeftY();
            leftX  = () -> -driverPS5.getLeftX();
            rightX = () -> -driverPS5.getRightX();
        } else {
            leftY  = () -> -driverXbox.getLeftY();
            leftX  = () -> -driverXbox.getLeftX();
            rightX = () -> -driverXbox.getRightX();
        }

        robotDrive.setDefaultCommand(
            new TeleopDriveCommand(
                robotDrive,
                leftY,    // forward / backward (sign-inverted per FRC convention)
                leftX,    // left / right       (sign-inverted)
                rightX,   // rotation            (sign-inverted)
                OIConstants.kFieldRelative,
                OIConstants.kRateLimited
            )
        );

        // Set the speed multiplier AFTER registering the default command.
        robotDrive.setSpeedPercent();
    }

    // ── Button bindings ───────────────────────────────────────────────────────

    private void configureBindings() {

        Trigger alignToTag, goToPosition, zeroHeading, autoScoring, goToSelectedPose;
        DoubleSupplier leftY, leftX;

        if (driverPS5 != null) {
            alignToTag       = driverPS5.square();
            goToPosition     = driverPS5.circle();
            zeroHeading      = driverPS5.triangle();
            autoScoring      = driverPS5.options();
            goToSelectedPose = driverPS5.cross();
            leftY = () -> -driverPS5.getLeftY();
            leftX = () -> -driverPS5.getLeftX();
        } else {
            alignToTag       = driverXbox.x();
            goToPosition     = driverXbox.b();
            zeroHeading      = driverXbox.y();
            autoScoring      = driverXbox.start();
            goToSelectedPose = driverXbox.a();
            leftY = () -> -driverXbox.getLeftY();
            leftX = () -> -driverXbox.getLeftX();
        }

        // Square / X — orient the robot to face a fixed point on the field while
        // still allowing the driver to strafe around it.
        alignToTag.whileTrue(
            new FacePointCommand(robotDrive, leftY, leftX, 11.945, 4.029, 2)
        );

        // Circle / B — drive to a fixed field position (testing / demo).
        goToPosition.whileTrue(
            new GoToPositionCommand(robotDrive, 10.0, 4.0, 0.0)
                .withTimeout(10.0)
        );

        // Triangle / Y — zero the gyro: treats the robot's current facing as field-forward.
        zeroHeading.onTrue(
            Commands.runOnce(() -> robotDrive.zeroHeading())
        );

        // Cross / A — drive to whichever pose the driver selected on SmartDashboard.
        // GoToPositionRelativeCommand automatically mirrors for red alliance.
        goToSelectedPose.whileTrue(
            Commands.defer(() -> {
                Pose2d pose = fieldPoseChooser.getSelected();
                if (pose == null) return Commands.none();
                return new GoToPositionRelativeCommand(
                    robotDrive,
                    pose.getX(),
                    pose.getY(),
                    pose.getRotation().getDegrees()
                ).withTimeout(10.0);
            }, Set.of(robotDrive))
        );

        // Options / Start — vision-align to AprilTag 4 for scoring.
        autoScoring.whileTrue(
            new AlignToAprilTagCommand(vision, robotDrive, 4)
                .withTimeout(5.0)
        );

        /* ── DISABLED: Ball Intake Buttons ────────────────────────────────────
         * CAN ID 99 is intentionally invalid — the physical hardware doesn't exist yet.
         * Un-comment these bindings AND update BallIntakeConstants (CAN ID + speeds).
         *
         * driverPS5.L1().whileTrue(
         *     new frc.robot.commands.ballintake.IntakeCommand(ballIntake));
         * driverPS5.R1().whileTrue(
         *     new frc.robot.commands.ballintake.EjectCommand(ballIntake));
         * driverPS5.touchpad().whileTrue(
         *     new frc.robot.commands.ballintake.HoldCommand(ballIntake));
         * driverPS5.PS().onTrue(
         *     new frc.robot.commands.ballintake.StopCommand(ballIntake));
         */
    }

    // ── Autonomous ────────────────────────────────────────────────────────────

    /**
     * Returns the autonomous command selected on SmartDashboard.
     *
     * <p>Reads the PathPlanner PID choosers (only populated if
     * {@link #configurePathPlannerPidChoosers()} is called in the constructor)
     * and reconfigures AutoBuilder with those gains before returning the command.
     *
     * @return the selected autonomous {@link Command}
     */
    public Command getAutonomousCommand() {
        PIDConstants translationPid = ppTranslationPidChooser.getSelected();
        PIDConstants rotationPid    = ppRotationPidChooser.getSelected();

        // Fall back to compile-time defaults if the PID choosers were never shown.
        if (translationPid == null) translationPid = AutoConstants.translationConstants;
        if (rotationPid    == null) rotationPid    = AutoConstants.rotationConstants;

        robotDrive.configureAutoBuilder(translationPid, rotationPid);
        return autoChooser.getSelected();
    }
}