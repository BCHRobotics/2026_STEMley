#!/usr/bin/env python3
"""Ad-hoc stop regression: execute Shooter's actual Java methods with fake IO.

No robot connection. This checks state/output decisions, not REV hardware behavior.
Run: python3 utilities/test_shooter_stop.py
"""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/frc/robot/subsystems/Shooter.java"


def method(source, name):
    import re
    match = re.search(r"(?:public|private) void " + name + r"\([^)]*\)\s*\{", source)
    start = match.start()
    opening = source.index("{", match.start())
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


class ShooterStopTest(unittest.TestCase):
    def test_stop_disable_and_reverse_outputs(self):
        source = SOURCE.read_text()
        methods = "\n".join(method(source, name) for name in
                            ("startShooter", "stopShooter", "killShooter", "stopFeeder", "updateMotors"))
        harness = '''
public class ShooterStopHarness {
  enum IdleMode { kCoast, kBrake }
  enum ControlType { kVelocity }
  static class DriverStation { static boolean enabled = true;
    static boolean isEnabled() { return enabled; } }
  static class Motor { double output = 1; void set(double x) { output = x; }
    void stopMotor() { output = 0; } }
  static class Controller { double reference = -1;
    void setSetpoint(double x, ControlType type) { reference = x; } }
  static class Timer { void reset() {} void start() {} void stop() {}
    boolean hasElapsed(double x) { return true; } }
  final Motor shooterMotor1 = new Motor(), shooterMotor2 = new Motor(),
    shooterMotor3 = new Motor(), shooterMotor4 = new Motor(), feederMotor = new Motor();
  final Controller flywheelController1 = new Controller();
  final Timer shooterSpinTimer = new Timer();
  boolean isShooterActive, isVortexSpeedShotActive, isFeederReversed;
  double currentFeederSpeed, feederSpeed = 1, targetRpm = 2500;
  static final double IDLE_RPM = 1500, VORTEX_SPEED_SHOT_TARGET_RPM = 6000;
  void setShooterIdleMode(IdleMode mode) {}
  boolean isCharged() { return true; }
  boolean isVortexSpeedShotReady() { return true; }
  void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
''' + methods + '''
  public static void main(String[] args) { new ShooterStopHarness().check(); }
  void check() {
    startShooter(); currentFeederSpeed = 1; updateMotors();
    require(flywheelController1.reference == targetRpm, "normal shooting target changed");
    require(feederMotor.output == 1, "normal feeding broken");
    stopShooter(); stopFeeder(); flywheelController1.reference = -1; updateMotors();
    require(flywheelController1.reference == -1, "Stop still commands an idle velocity");
    require(shooterMotor1.output == 0 && feederMotor.output == 0, "Stop leaves motor power");
    startShooter(); currentFeederSpeed = 1; DriverStation.enabled = false; updateMotors();
    DriverStation.enabled = true; flywheelController1.reference = -1; updateMotors();
    require(!isShooterActive && !isVortexSpeedShotActive, "Disable leaves run state latched");
    require(flywheelController1.reference == -1 && feederMotor.output == 0, "Re-enable restarts shooter");
    isFeederReversed = true; updateMotors();
    require(feederMotor.output == -feederSpeed, "Explicit reverse feeder broken");
    stopShooter(); stopFeeder(); updateMotors();
    require(feederMotor.output == 0 && !isFeederReversed, "Stop fails to clear reverse request");
    startShooter(); currentFeederSpeed = 1; killShooter(); updateMotors();
    require(!isShooterActive && feederMotor.output == 0, "Kill leaves active outputs");
    System.out.println("PASS: normal shooting, stop, disable/re-enable, reverse, reverse-stop, kill");
  }
}
'''
        with tempfile.TemporaryDirectory(prefix="hermes-shooter-", dir=os.environ.get("TMPDIR")) as scratch:
            path = Path(scratch) / "ShooterStopHarness.java"
            path.write_text(harness)
            subprocess.run(["javac", str(path)], check=True)
            result = subprocess.run(["java", "-cp", scratch, "ShooterStopHarness"], text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            print(result.stdout.strip())


if __name__ == "__main__":
    unittest.main()
