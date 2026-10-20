// TUNING RULES OF THUMB
// ----------------------
//  • Always tune feedforward before feedback (F before P before D before I).
//  • Use SmartDashboard live-tuning (updateTunables) — no redeploy required.
//    Keys live under "Shooter/Tuning/*" and apply on change.
//  • Keep maxOutput ≤ 0.85 until gains are stable to protect the mechanism.
//  • Once gains are finalized, change PersistMode to kPersistParameters and
//    commit the constants in code so they survive a power cycle.
//
// =============================================================================

package frc.robot.subsystems;

import com.revrobotics.PersistMode;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.SparkBase.ControlType;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.FeedbackSensor;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import com.revrobotics.spark.config.SparkFlexConfig;
import frc.robot.Constants.NavigationConstants;
import frc.robot.Constants.ShooterConstants;
import frc.robot.Constants.VortexMotorConstants;

import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj.Timer;
import org.littletonrobotics.junction.Logger;

public class Shooter extends SubsystemBase {

    private final SparkMax feederMotor;
    private final SparkFlex shooterMotor1;
    private final SparkFlex shooterMotor2;
    private final SparkFlex shooterMotor3;
    private final SparkFlex shooterMotor4;
    private final SparkFlexConfig shooter1Config;
    private final SparkFlexConfig shooter2Config;
    private final SparkFlexConfig shooter3Config;
    private final SparkFlexConfig shooter4Config;
    private final Drivetrain drivetrain;
    private final Timer shooterSpinTimer = new Timer();

    // On-board closed-loop controller runs the PID at 1 kHz on the SPARK Flex
    // (vs. 50 Hz if run on the RoboRIO with WPILib PIDController)
    private final SparkClosedLoopController flywheelController1;

    private static final double VORTEX_SPEED_SHOT_TARGET_RPM = VortexMotorConstants.kFreeSpeedRpm * 0.95;
    private static final double VORTEX_SPEED_SHOT_READY_RPM = VORTEX_SPEED_SHOT_TARGET_RPM * 0.50;

    // Live-tunable values, exposed through Elastic/SmartDashboard. These start at
    // the ShooterConstants defaults and are refreshed every loop while tuning.
    private static final String TUNING_PREFIX = "Shooter/Tuning/";
    private double kP;
    private double kI;
    private double kD;
    private double kF;
    private double maxOutput;
    private double readyRpm;
    private double feederSpeed;
    private double targetRpm;

    private boolean isShooterActive  = false;
    private boolean isVortexSpeedShotActive = false;
    private boolean isFeederReversed = false;
    private double  currentFeederSpeed = 0.0;

    public Shooter(Drivetrain drivetrain) {
        this.drivetrain = drivetrain;
        feederMotor   = new SparkMax(ShooterConstants.FEEDER_CAN_ID,   MotorType.kBrushless);
        shooterMotor1 = new SparkFlex(ShooterConstants.SHOOTER1_CAN_ID, MotorType.kBrushless);
        shooterMotor2 = new SparkFlex(ShooterConstants.SHOOTER2_CAN_ID, MotorType.kBrushless);
        // CHANGE #1: Four NEO Vortex motors drive one shooter through SPARK Flex controllers.
        // Only motor 1 controls RPM; motors 2–4 follow its output.
        shooterMotor3 = new SparkFlex(ShooterConstants.SHOOTER3_CAN_ID, MotorType.kBrushless);
        shooterMotor4 = new SparkFlex(ShooterConstants.SHOOTER4_CAN_ID, MotorType.kBrushless);
        shooter1Config = new SparkFlexConfig();
        shooter2Config = new SparkFlexConfig();
        shooter3Config = new SparkFlexConfig();
        shooter4Config = new SparkFlexConfig();

        flywheelController1 = shooterMotor1.getClosedLoopController();

        loadTuningDefaults();
        publishTuningDefaults();

        configureMotors();
        publishTelemetry();
    }

    private void loadTuningDefaults() {
        kP = ShooterConstants.kP1;
        kI = ShooterConstants.kI1;
        kD = ShooterConstants.kD1;
        kF = ShooterConstants.kF1;
        maxOutput = ShooterConstants.maxOutput;
        readyRpm = ShooterConstants.readyRpm;
        feederSpeed = ShooterConstants.feederSpeed;
        targetRpm = ShooterConstants.targetRpm;
    }

    /**
     * Publishes the tuning defaults once. setDefault* only writes the value if the
     * key is missing, so the periodic loop never overwrites values typed into
     * Elastic. The keys are marked persistent so the last values survive a robot
     * restart (stored in networktables.json on the roboRIO).
     */
    private void publishTuningDefaults() {
        publishDefaultNumber("P", kP);
        publishDefaultNumber("I", kI);
        publishDefaultNumber("D", kD);
        publishDefaultNumber("F", kF);
        publishDefaultNumber("MaxOutput", maxOutput);
        publishDefaultNumber("ReadyRPM", readyRpm);
        publishDefaultNumber("FeederSpeed", feederSpeed);
        publishDefaultNumber("TestRPM", targetRpm);
        SmartDashboard.setDefaultBoolean(TUNING_PREFIX + "UseDistanceForTargetRPM", true);
        SmartDashboard.setPersistent(TUNING_PREFIX + "UseDistanceForTargetRPM");
    }

    private void publishDefaultNumber(String name, double defaultValue) {
        SmartDashboard.setDefaultNumber(TUNING_PREFIX + name, defaultValue);
        SmartDashboard.setPersistent(TUNING_PREFIX + name);
    }

    private void publishTelemetry() {
        SmartDashboard.putNumber("Shooter/Distance", getHubDistance());
        SmartDashboard.putNumber("Shooter/TargetRPM", targetRpm);
        SmartDashboard.putNumber("Shooter/ReadyRPM", readyRpm);
        SmartDashboard.putNumber("Shooter/Shooter1RPM", shooterMotor1.getEncoder().getVelocity());
        SmartDashboard.putNumber("Shooter/Shooter2RPM", shooterMotor2.getEncoder().getVelocity());
        SmartDashboard.putNumber("Shooter/Shooter3RPM", shooterMotor3.getEncoder().getVelocity());
        SmartDashboard.putNumber("Shooter/Shooter4RPM", shooterMotor4.getEncoder().getVelocity());
        SmartDashboard.putNumber("Shooter/AppliedFeederSpeed", currentFeederSpeed);
        SmartDashboard.putBoolean("Shooter/VortexSpeedShotActive", isVortexSpeedShotActive);
        SmartDashboard.putBoolean("Shooter/VortexSpeedShotReady", isVortexSpeedShotReady());

        Logger.recordOutput("Shooter/Distance", getHubDistance());
        Logger.recordOutput("Shooter/TargetRPM", targetRpm);
        Logger.recordOutput("Shooter/ReadyRPM", readyRpm);
        Logger.recordOutput("Shooter/Shooter1RPM", shooterMotor1.getEncoder().getVelocity());
        Logger.recordOutput("Shooter/Shooter2RPM", shooterMotor2.getEncoder().getVelocity());
        Logger.recordOutput("Shooter/Shooter3RPM", shooterMotor3.getEncoder().getVelocity());
        Logger.recordOutput("Shooter/Shooter4RPM", shooterMotor4.getEncoder().getVelocity());
        Logger.recordOutput("Shooter/AppliedFeederSpeed", currentFeederSpeed);
        Logger.recordOutput("Shooter/VortexSpeedShotActive", isVortexSpeedShotActive);
        Logger.recordOutput("Shooter/RPM", shooterMotor1.getEncoder().getVelocity());
        Logger.recordOutput("Shooter/Charged", isCharged());
        Logger.recordOutput("Shooter/Active", isShooterActive);
        Logger.recordOutput("Shooter/Motor1CurrentAmps", shooterMotor1.getOutputCurrent());
        Logger.recordOutput("Shooter/Motor2CurrentAmps", shooterMotor2.getOutputCurrent());
        Logger.recordOutput("Shooter/Motor3CurrentAmps", shooterMotor3.getOutputCurrent());
        Logger.recordOutput("Shooter/Motor4CurrentAmps", shooterMotor4.getOutputCurrent());
        Logger.recordOutput("Shooter/FeederCurrentAmps", feederMotor.getOutputCurrent());
        Logger.recordOutput("Shooter/RPMError", shooterMotor1.getEncoder().getVelocity() - targetRpm);
        Logger.recordOutput("Shooter/Tuning/P", kP);
        Logger.recordOutput("Shooter/Tuning/I", kI);
        Logger.recordOutput("Shooter/Tuning/D", kD);
        Logger.recordOutput("Shooter/Tuning/F", kF);
        Logger.recordOutput("Shooter/Tuning/MaxOutput", maxOutput);
    }

    private void applyClosedLoopConfig(SparkFlexConfig config, double p, double i, double d, double f) {
        config.closedLoop
            .feedbackSensor(FeedbackSensor.kPrimaryEncoder)
            .p(p)
            .i(i)
            .d(d)
            .outputRange(0, maxOutput);

        config.closedLoop.feedForward.kV(f);
    }

    private void configureMotors() {
        // --- Feeder motor ---
        SparkMaxConfig feederConfig = new SparkMaxConfig();
        feederConfig
            .inverted(true)
            .idleMode(IdleMode.kCoast);  // Hard-stop when commanded to 0

        // --- Primary flywheel motor (closed-loop velocity) ---
        // Reuse the instance variable shooter1Config
        shooter1Config
            .inverted(true)//set to true
            .idleMode(IdleMode.kCoast);  // Coast so the flywheel spins down naturally

        shooter1Config.encoder
            .velocityConversionFactor(1.0);  // RPM (native NEO units)

        applyClosedLoopConfig(
            shooter1Config,
            kP,
            kI,
            kD,
            kF);

        // CHANGE #2: All three followers are inverted relative to the leader.
        // Confirm the physical motor mounting agrees with this direction before running.
        configureFollower(shooter2Config, true);
        configureFollower(shooter3Config, false);
        configureFollower(shooter4Config, true);

        feederMotor.configure(
            feederConfig,
            ResetMode.kResetSafeParameters,
            PersistMode.kPersistParameters);

        shooterMotor1.configure(
            shooter1Config,
            ResetMode.kResetSafeParameters,
            PersistMode.kPersistParameters);

        shooterMotor2.configure(
            shooter2Config,
            ResetMode.kResetSafeParameters,
            PersistMode.kPersistParameters);
        shooterMotor3.configure(
            shooter3Config,
            ResetMode.kResetSafeParameters,
            PersistMode.kPersistParameters);
        shooterMotor4.configure(
            shooter4Config,
            ResetMode.kResetSafeParameters,
            PersistMode.kPersistParameters);
    }

    private void configureFollower(SparkFlexConfig config, boolean inverted) {
        config
            .follow(ShooterConstants.SHOOTER1_CAN_ID, inverted)
            .idleMode(IdleMode.kCoast);
        config.encoder.velocityConversionFactor(1.0);  // Native NEO Vortex RPM
    }

    /** @return true when the flywheel has reached the ready RPM threshold */
    public boolean isCharged() {
        double currentVelocity1 = shooterMotor1.getEncoder().getVelocity();
        double allowedError = Math.abs(targetRpm - readyRpm);

        Boolean isCharged1 = Math.abs(currentVelocity1 - targetRpm) <= allowedError;
        return isCharged1;

    }

    /** Spins up the flywheel to {@link #targetRpm} */
    public void startShooter() {
        isShooterActive = true;
        isVortexSpeedShotActive = false;

        // Restore coast mode
        setShooterIdleMode(IdleMode.kCoast);

        shooterSpinTimer.reset();
        shooterSpinTimer.start();
    }

    /** Spins the shooter to 95% of NEO Vortex free speed and auto-runs the feeder at 80% of target RPM. */
    public void startVortexSpeedShot() {
        isShooterActive = false;
        isVortexSpeedShotActive = true;

        setShooterIdleMode(IdleMode.kCoast);
    }

    public boolean isVortexSpeedShotReady() {
        double currentVelocity1 = shooterMotor1.getEncoder().getVelocity();
        return currentVelocity1 >= VORTEX_SPEED_SHOT_READY_RPM;
    }

    /** No-op; subsystem state is already initialized in the constructor */
    public void initialize() {}

    /** Removes flywheel and feeder power; the flywheel coasts down naturally. */
    public void stopShooter() {
        // CHANGE #4: Stop clears every latched request, not just the shoot flag.
        // This also prevents a dashboard run from resuming after disable/re-enable.
        isShooterActive = false;
        isVortexSpeedShotActive = false;
        isFeederReversed = false;
        currentFeederSpeed = 0.0;
        shooterSpinTimer.stop();
        shooterSpinTimer.reset();
        shooterMotor1.stopMotor();
        feederMotor.stopMotor();
        // Followers remain configured to follow the now-stopped leader.
    }
    public void killShooter() {
        stopShooter();

        // Switch to brake mode and stop motors
        setShooterIdleMode(IdleMode.kBrake);
        shooterMotor1.stopMotor();
        shooterMotor2.stopMotor();
        shooterMotor3.stopMotor();
        shooterMotor4.stopMotor();
    }

    private void setShooterIdleMode(IdleMode mode) {
        // CHANGE #3: Change all four idle modes while retaining the followers' configuration.
        shooter1Config.idleMode(mode);
        shooter2Config.idleMode(mode);
        shooter3Config.idleMode(mode);
        shooter4Config.idleMode(mode);

        shooterMotor1.configure(shooter1Config, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
        shooterMotor2.configure(shooter2Config, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
        shooterMotor3.configure(shooter3Config, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
        shooterMotor4.configure(shooter4Config, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
    }

    /** Runs the feeder belt at {@link #feederSpeed} */
    public void startFeeder() {
        currentFeederSpeed = feederSpeed;
    }

    public void toggleReverseFeeder() {
        currentFeederSpeed = -feederSpeed;
    }

    /**
     * Runs the feeder in reverse regardless of shooter state.
     * Safe to call while the shooter is stopped — does not interact with
     * {@link #isCharged()} since that gate only applies when
     * {@code isShooterActive} is true.
     */
    public void reverseFeeder() {
        isFeederReversed = true;
    }

    /** Stops the feeder that was running in reverse via {@link #reverseFeeder()}. */
    public void stopReverseFeeder() {
        isFeederReversed = false;
    }

    /** Stops the feeder belt */
    public void stopFeeder() {
        currentFeederSpeed = 0.0;
    }

    /**
     * Calculates the target RPM based on the distance from the center of the hub.
     * 
     * @param distanceFromHub the distance in meters from the center of the hub
     * @return the target RPM for the shooter flywheel
     */
    public double calculateRpmFromDistance(double distanceFromHub) {
        // Linear interpolation model: RPM increases with distance
        // Adjust these constants based on your shooter's ballistics
        final double MIN_DISTANCE = 1.0;      // Minimum shooting distance (meters)
        final double MAX_DISTANCE = 3.0;     // Maximum shooting distance (meters)
        final double MIN_RPM = 1500.0;         // RPM at minimum distance
        final double MAX_RPM = 3800.0 * 0.8 ;         // RPM at maximum distance

        // Clamp distance to valid range
        double clampedDistance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distanceFromHub));

        // Linear interpolation
        double rpm = (MIN_RPM + (clampedDistance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE) * (MAX_RPM - MIN_RPM)) * 1.00;

        return rpm;
    }

    private Translation2d getAllianceHubCenter() {
        Alliance alliance = DriverStation.getAlliance().orElse(Alliance.Blue);
        return (alliance == Alliance.Red)
            ? NavigationConstants.kRedAllianceHubCenter
            : NavigationConstants.kBlueAllianceHubCenter;
    }

    private double getHubDistance() {
        return drivetrain.getPose().getTranslation().getDistance(getAllianceHubCenter());
    }

    @Override
    public void periodic() {
        updateTunables();
        updateMotors();
        publishTelemetry();

        // Publish telemetry to SmartDashboard / Shuffleboard
        SmartDashboard.putNumber("Shooter/RPM",     shooterMotor1.getEncoder().getVelocity());
        SmartDashboard.putBoolean("Shooter/Charged", isCharged());
        SmartDashboard.putBoolean("Shooter/Active",  isShooterActive);
    }

    private void updateTunables() {
        double newF = Math.max(0.0, getTunable("F", kF));
        double newP = Math.max(0.0, getTunable("P", kP));
        double newI = Math.max(0.0, getTunable("I", kI));
        double newD = Math.max(0.0, getTunable("D", kD));
        double newMaxOutput = MathUtil.clamp(getTunable("MaxOutput", maxOutput), 0.0, 1.0);

        feederSpeed = MathUtil.clamp(getTunable("FeederSpeed", feederSpeed), 0.0, 1.0);

        boolean useDistance = SmartDashboard.getBoolean(TUNING_PREFIX + "UseDistanceForTargetRPM", true);
        targetRpm = useDistance
            ? calculateRpmFromDistance(getHubDistance())
            : Math.max(0.0, getTunable("TestRPM", targetRpm));

        // ReadyRPM is the lower bound of the "charged" window; keep it at or below
        // the setpoint so a low TestRPM cannot make isCharged() true immediately.
        readyRpm = MathUtil.clamp(getTunable("ReadyRPM", readyRpm), 0.0, targetRpm);

        if (newF != kF || newP != kP || newI != kI || newD != kD || newMaxOutput != maxOutput) {
            kF = newF;
            kP = newP;
            kI = newI;
            kD = newD;
            maxOutput = newMaxOutput;

            applyClosedLoopConfig(
                shooter1Config,
                kP,
                kI,
                kD,
                kF);

            // Re-apply configuration to the motor controller
            // We use kNoResetSafeParameters to avoid resetting other settings
            // We use kNoPersistParameters to avoid wearing out flash memory during tuning
            shooterMotor1.configure(
                shooter1Config,
                ResetMode.kNoResetSafeParameters,
                PersistMode.kNoPersistParameters);
        }
    }

    private double getTunable(String name, double defaultValue) {
        return SmartDashboard.getNumber(TUNING_PREFIX + name, defaultValue);
    }

    private void updateMotors() {
        double feederOutput = 0.0;

        if (!DriverStation.isEnabled()) {
            stopShooter();
        } else if (isVortexSpeedShotActive) {
            flywheelController1.setSetpoint(VORTEX_SPEED_SHOT_TARGET_RPM, ControlType.kVelocity);
            if (isVortexSpeedShotReady()) {
                feederOutput = feederSpeed;
            }
        } else if (isShooterActive) {
            // Delegate PID calculation to the leader SPARK Flex (runs at 1 kHz on-board).
            flywheelController1.setSetpoint(targetRpm, ControlType.kVelocity);
            if (isCharged() && shooterSpinTimer.hasElapsed(0.5)) {  // Add a short delay after reaching target RPM
                feederOutput = currentFeederSpeed;
            }
        } else if (isFeederReversed) {
            // CHANGE #5: Reversing the feeder must not spin an otherwise stopped flywheel.
            shooterMotor1.stopMotor();
            feederOutput = -feederSpeed;
        } else {
            // No active shoot request means no powered idle RPM.
            shooterMotor1.stopMotor();
        }

        feederMotor.set(feederOutput);
    }
}
