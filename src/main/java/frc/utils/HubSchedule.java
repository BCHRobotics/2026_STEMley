package frc.utils;

/**
 * REBUILT hub-shift schedule — pure Java, no WPILib dependency, so it can be unit tested.
 *
 * <p>Teleop is 140 s and {@code DriverStation.getMatchTime()} counts DOWN from 140:
 * <pre>
 *   140 → 130  transition shift   both hubs active
 *   130 → 105  shift 1
 *   105 →  80  shift 2
 *    80 →  55  shift 3
 *    55 →  30  shift 4
 *    30 →   0  end game            both hubs active
 * </pre>
 * The alliance that scored MORE fuel in auto goes inactive first. The field sends that
 * alliance as a single character ('R' or 'B') ~3 s after auto ends (empty before that).
 * That alliance's hub is therefore active in shifts 2 and 4; the other alliance's in 1 and 3.
 *
 * <p>FMS match time is approximate — treat the warning window as a cue, not a guarantee.
 */
public final class HubSchedule {
    private HubSchedule() {}

    /** Match time (s remaining) at which the transition shift ends. */
    public static final double kTransitionEnd = 130.0;
    /** Match time (s remaining) at which the end game begins. */
    public static final double kEndgameStart = 30.0;

    /**
     * @param known         false when the hub state can't be determined (no match timer, or
     *                      game data missing during a shift) — show an "unknown" indicator
     * @param active        true if our alliance's hub is currently scoring
     * @param changingSoon  true if {@code active} will flip within the warning window
     */
    public record Status(boolean known, boolean active, boolean changingSoon) {}

    /**
     * @param matchTime    seconds remaining in teleop ({@code DriverStation.getMatchTime()}),
     *                     negative if unavailable
     * @param gameData     {@code DriverStation.getGameSpecificMessage()} — may be null/empty
     * @param isRed        true if we are the red alliance
     * @param warnSeconds  how far ahead to flag an upcoming flip
     */
    public static Status compute(double matchTime, String gameData, boolean isRed, double warnSeconds) {
        if (matchTime < 0) {
            return new Status(false, true, false);
        }

        Boolean redInactiveFirst = parse(gameData);
        if (redInactiveFirst == null) {
            // No usable data. Fine in transition / end game (hub is active for everyone),
            // but during a shift we genuinely don't know.
            return new Status(inFreeWindow(matchTime), true, false);
        }

        boolean now  = activeAt(matchTime, redInactiveFirst, isRed);
        boolean soon = activeAt(matchTime - warnSeconds, redInactiveFirst, isRed);
        return new Status(true, now, now != soon);
    }

    /** True when both hubs are active regardless of auto result. */
    private static boolean inFreeWindow(double t) {
        return t > kTransitionEnd || t <= kEndgameStart;
    }

    private static boolean activeAt(double t, boolean redInactiveFirst, boolean isRed) {
        if (inFreeWindow(t)) {
            return true;
        }
        boolean shift1Active = isRed ? !redInactiveFirst : redInactiveFirst;
        int shift = t > 105.0 ? 1 : t > 80.0 ? 2 : t > 55.0 ? 3 : 4;
        // Odd shifts follow shift 1; even shifts are the opposite.
        return (shift % 2 == 1) == shift1Active;
    }

    /** @return TRUE if red goes inactive first, FALSE if blue, null if missing/invalid. */
    private static Boolean parse(String gameData) {
        if (gameData == null || gameData.isEmpty()) {
            return null;
        }
        switch (Character.toUpperCase(gameData.charAt(0))) {
            case 'R': return Boolean.TRUE;
            case 'B': return Boolean.FALSE;
            default:  return null;
        }
    }
}
