package frc.robot.subsystems;

import edu.wpi.first.wpilibj.AddressableLED;
import edu.wpi.first.wpilibj.AddressableLEDBuffer;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.LEDConstants;

/**
 * Driver-feedback LED strip (WS2812B) for REBUILT.
 *
 * <p>One state at a time; priority is decided in {@code RobotContainer.updateLEDState()}.
 * Every state is a solid or flashing color on the whole strip, except the intake/shooter
 * combo (see below).
 *
 * <ul>
 *   <li>{@code DEPLOY}            – solid white, first 2 s after boot</li>
 *   <li>{@code GYRO_FAULT}        – flashing red-orange / red, NavX disconnected</li>
 *   <li>{@code AUTO}              – solid orange</li>
 *   <li>{@code TEST}              – solid purple</li>
 *   <li>{@code MATCH_END}         – flashing white, ≤ 10 s left</li>
 *   <li>{@code ENDGAME}           – flashing yellow, ≤ 30 s left</li>
 *   <li>{@code SHIFT_ON_WARNING}  – flashing green, our hub opens in a few seconds</li>
 *   <li>{@code SHIFT_OFF_WARNING} – flashing magenta, our hub closes in a few seconds</li>
 *   <li>{@code POSE_VALID}        – vision lock: distance gradient, or flashing lime</li>
 *   <li>{@code INTAKE}            – purple</li>
 *   <li>{@code SHOOTER}           – orange</li>
 *   <li>{@code TELEOP}            – solid green (default)</li>
 * </ul>
 *
 * <p><b>Intake + shooter together:</b> first and last
 * {@link LEDConstants#kIntakeEdgeFraction} of the strip purple (intake), the middle orange
 * (shooter). If only one is running the whole strip is that one's color.
 */
public class LED extends SubsystemBase {

    public enum State {
        DEPLOY,
        GYRO_FAULT,
        AUTO,
        TEST,
        MATCH_END,
        ENDGAME,
        POSE_VALID,
        INTAKE,
        SHOOTER,
        SHIFT_ON_WARNING,
        SHIFT_OFF_WARNING,
        TELEOP
    }

    // ── Colors [R, G, B] ──────────────────────────────────────────────────────
    private static final int[] OFF        = {  0,   0,   0};
    private static final int[] WHITE      = {100, 100, 100};
    private static final int[] RED        = {255,   0,   0};
    private static final int[] RED_ORANGE = {255,  50,   0};
    private static final int[] ORANGE     = {255,  80,   0};
    private static final int[] YELLOW     = {255, 180,   0};
    private static final int[] LIME       = {130, 255,   0};
    private static final int[] GREEN      = {  0, 200,   0};
    private static final int[] PURPLE     = {160,   0, 200};
    private static final int[] MAGENTA    = {220,   0, 150};

    // ── Hardware ──────────────────────────────────────────────────────────────
    private final AddressableLED       m_led;
    private final AddressableLEDBuffer m_buffer;

    // ── State ─────────────────────────────────────────────────────────────────
    private State   m_state         = State.TELEOP;
    private boolean m_outputEnabled = true;

    private boolean m_intakeRunning  = false;
    private boolean m_shooterRunning = false;

    /** Last vision distance (m) and when it was set — expires so stale data can't linger. */
    private double m_distanceMeters = -1.0;
    private double m_distanceStamp  = 0.0;

    /** Reused by {@link #distanceToColor} so the 50 Hz loop doesn't allocate. */
    private final int[] m_scratch = new int[3];

    private final double m_bootTime;
    private static final double kDeployDuration = 2.0;

    // ── Constructor ───────────────────────────────────────────────────────────

    /** Constructed in RobotContainer (→ robotInit), so the deploy window starts at code boot. */
    public LED() {
        m_led    = new AddressableLED(LEDConstants.kPort);
        m_buffer = new AddressableLEDBuffer(LEDConstants.kLength);
        m_led.setLength(LEDConstants.kLength);
        m_led.start();
        m_bootTime = Timer.getFPGATimestamp();
    }

    // ── Public API (call once per loop from RobotContainer.updateLEDState) ────

    public void setState(State state) { m_state = state; }

    /** False blanks the strip (used while disabled). */
    public void setOutputEnabled(boolean enabled) { m_outputEnabled = enabled; }

    /**
     * Which mechanisms are running right now. Used when the state is INTAKE or SHOOTER:
     * both true → split strip, one true → that color on the whole strip.
     */
    public void setMechanisms(boolean intakeRunning, boolean shooterRunning) {
        m_intakeRunning  = intakeRunning;
        m_shooterRunning = shooterRunning;
    }

    /**
     * Distance to the target in metres. Call every loop while vision is valid; it expires
     * after {@link LEDConstants#kDistanceTimeoutSeconds} and POSE_VALID falls back to flashing lime.
     */
    public void setDistanceToTarget(double meters) {
        m_distanceMeters = meters;
        m_distanceStamp  = Timer.getFPGATimestamp();
    }

    /** True for the first {@value #kDeployDuration} s after boot. */
    public boolean isDeployAnimationPlaying() {
        return Timer.getFPGATimestamp() - m_bootTime < kDeployDuration;
    }

    // ── Periodic ──────────────────────────────────────────────────────────────

    @Override
    public void periodic() {
        if (!m_outputEnabled) {
            fill(OFF);
        } else if (m_state == State.INTAKE || m_state == State.SHOOTER) {
            renderMechanisms();
        } else {
            fill(solidColor(m_state));
        }
        m_led.setData(m_buffer);

        SmartDashboard.putString("LED State", m_state.toString());
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /** Exhaustive switch expression: adding a State without a color is a compile error. */
    private int[] solidColor(State state) {
        return switch (state) {
            case DEPLOY            -> WHITE;
            case GYRO_FAULT        -> flashOn() ? RED_ORANGE : RED;
            case AUTO              -> ORANGE;
            case TEST              -> PURPLE;
            case MATCH_END         -> flashOn() ? WHITE  : OFF;
            case ENDGAME           -> flashOn() ? YELLOW : OFF;
            case SHIFT_ON_WARNING  -> flashOn() ? GREEN   : OFF;
            case SHIFT_OFF_WARNING -> flashOn() ? MAGENTA : OFF;
            case POSE_VALID        -> {
                double d = freshDistance();
                yield d >= 0 ? distanceToColor(d) : (flashOn() ? LIME : OFF);
            }
            case INTAKE            -> PURPLE;   // normally handled by renderMechanisms()
            case SHOOTER           -> ORANGE;   // normally handled by renderMechanisms()
            case TELEOP            -> GREEN;
        };
    }

    /** Intake only → purple, shooter only → orange, both → purple edges + orange middle. */
    private void renderMechanisms() {
        boolean intake  = m_intakeRunning;
        boolean shooter = m_shooterRunning;
        if (!intake && !shooter) { // flags not supplied — fall back to the state itself
            intake  = (m_state == State.INTAKE);
            shooter = (m_state == State.SHOOTER);
        }

        if (intake && shooter) {
            int len  = m_buffer.getLength();
            int edge = (int) Math.round(len * LEDConstants.kIntakeEdgeFraction);
            for (int i = 0; i < len; i++) {
                int[] c = (i < edge || i >= len - edge) ? PURPLE : ORANGE;
                m_buffer.setRGB(i, c[0], c[1], c[2]);
            }
        } else {
            fill(intake ? PURPLE : ORANGE);
        }
    }

    private void fill(int[] rgb) {
        for (int i = 0; i < m_buffer.getLength(); i++) {
            m_buffer.setRGB(i, rgb[0], rgb[1], rgb[2]);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Stateless flash, always in phase. {@link LEDConstants#kFlashPeriod} is the FULL
     * on+off cycle (0.25 s = 4 Hz).
     */
    private static boolean flashOn() {
        double period = LEDConstants.kFlashPeriod;
        return (Timer.getFPGATimestamp() % period) < (period / 2.0);
    }

    /** Distance in metres, or -1 if never set, unknown, or older than the timeout. */
    private double freshDistance() {
        boolean fresh = Timer.getFPGATimestamp() - m_distanceStamp < LEDConstants.kDistanceTimeoutSeconds;
        return fresh ? m_distanceMeters : -1.0;
    }

    /**
     * Maps distance to one solid color on red → yellow → green (far → close).
     * Writes into a reused array — don't hold the returned reference.
     */
    private int[] distanceToColor(double distanceMeters) {
        double range = LEDConstants.kDistanceFarMeters - LEDConstants.kDistanceCloseMeters;
        double t = 1.0 - (distanceMeters - LEDConstants.kDistanceCloseMeters) / range;
        t = Math.max(0.0, Math.min(1.0, t));

        if (t < 0.5) {
            double s = t * 2.0;
            m_scratch[0] = 255;
            m_scratch[1] = (int) (180.0 * s);
        } else {
            double s = (t - 0.5) * 2.0;
            m_scratch[0] = (int) (255.0 * (1.0 - s));
            m_scratch[1] = (int) (180.0 + 75.0 * s);
        }
        m_scratch[2] = 0;
        return m_scratch;
    }
}