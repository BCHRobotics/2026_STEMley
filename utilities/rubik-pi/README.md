# Rubik Pi support utilities

This folder contains a **manual PhotonVision updater**, not robot commands. It runs on the Rubik Pi's Linux system and is not deployed to the roboRIO by GradleRIO.

## Read before running

- [Student guide index](../../docs/rubik-pi/README.md)
- [Update walkthrough, safety checks and rollback](../../docs/rubik-pi/PHOTONVISION_UPDATE.md)
- [Source build walkthrough](../../docs/rubik-pi/PHOTONVISION_BUILD.md)
- [Cockpit file/system management](../../docs/rubik-pi/COCKPIT.md)

## Files

| File | Purpose |
|---|---|
| `update-photonvision.sh` | Validate an explicitly selected Linux ARM64 JAR; stop, back up, replace and start PhotonVision |
| `tests/test_update_photonvision.py` | Exercise the updater in temporary folders with mocked service/HTTP commands; never update a real service |

The script is a byte-for-byte copy of the helper prepared and dry-run-tested on the reference Rubik Pi. Its paths are deliberately scoped to that PhotonVision layout. Read the constants and confirm your device's service configuration before using it elsewhere.

**On the Pi** after copying the script into the `photon` user's home directory:

```bash
chmod 750 ~/update-photonvision.sh
~/update-photonvision.sh --dry-run /home/photon/EXACT_FILENAME-linuxarm64.jar
```

Substitute a real filename, compare checksums, and obtain activation approval. Omit `--dry-run` only when you intend to interrupt vision and install the selected build. The helper uses sudo as needed, but does not install Java, change network settings, update the OS, reboot, or modify the systemd service definition.

## Local tests — no deployment

Use Linux, Python 3, Bash, and standard tools (`tar`, `flock`, `sha256sum`, `realpath`, `mktemp`). Supply an existing built/downloaded ARM64 PhotonVision JAR as input. The test does not run that JAR. **Do not run these tests with sudo.**

From the robot repository root:

```bash
bash -n utilities/rubik-pi/update-photonvision.sh
python3 utilities/rubik-pi/tests/test_update_photonvision.py \
  --jar /path/to/photonvision-EXACT_VERSION-linuxarm64.jar
```

The test checks:

1. A valid JAR's dry-run makes no service calls and does not modify the installed-file fixture.
2. A missing source is rejected before service operations.
3. A deliberately corrupted ZIP payload is rejected.
4. An isolated simulated update replaces the target, preserves configuration, writes a backup, and calls stop before start.

The tests create a **temporary copy** of the script with test-only paths, mocked `systemctl`/`curl`/`sleep`/`install`, and disabled privilege escalation. They remove their temporary directory on completion. The test fixture's old JAR is a marker file, not a claimed runnable PhotonVision build.

Passing these tests does not prove a real service restart, NPU inference, robot connection or accurate pose. Those checks require a separately approved bench test. The script itself was also successfully dry-run-tested on the real Pi, without replacing its installed JAR.

## Error handling and boundaries

The helper backs up the stopped application's full directory, so configuration SQLite writes are not in flight. It stages on the same filesystem and atomically renames the new JAR into place. A lock prevents concurrent updater runs.

It deliberately does **not** automatically roll back after startup: the new application may migrate its database. A mentor must preserve failed-state evidence and restore a matched JAR/configuration backup if needed. An error can leave the new JAR installed. Native ABI compatibility and season-compatible PhotonLib are outside this script's packaging checks.

Changes to this tool need script tests and a real-device dry-run before another mentor-approved activation. Keep passwords, private keys, databases, full backups and compiled JARs out of Git.
