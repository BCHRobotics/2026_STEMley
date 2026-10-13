# Updating PhotonVision on the Rubik Pi 3

## What is being updated?

A **JAR** (Java archive) is a package containing a Java application. PhotonVision's deployable "fat JAR" also packages its web interface, dependencies, and platform-specific native libraries. The Rubik runs Linux ARM64, so select a `photonvision-...-linuxarm64.jar`, not a Windows/macOS/x86 build, library-only JAR, or a `.tar.xz` operating-system image.

For ordinary supported same-season release updates, PhotonVision recommends the web interface's **Settings → Offline Update** workflow. Check release notes first, especially across seasons. This team script supports our manually staged development builds and provides a stopped-configuration backup. It is not a reimaging tool.

**Building** produces a file. **Staging** copies it onto the Pi. **Activating** replaces the file used by the service and restarts the application. Only the last step interrupts camera processing.

## Before starting

Ask the programming lead or mentor to approve activation. Keep the robot disabled and make sure nobody is using vision during the restart.

- Confirm the exact JAR and its source/commit; do not choose "the newest-looking file".
- Check PhotonLib/season compatibility, release notes, and any database migrations. A newer JAR may permanently change the configuration format.
- Export configuration through PhotonVision as an extra portable backup.
- Check enough disk space for a full backup of `/opt/photonvision`, including models.
- Check the installed service and Java runtime; the script does **not** install Java or edit service configuration.

**Run on the Pi, inside SSH or Cockpit's Terminal:**

```bash
systemctl cat photonvision
/usr/lib/jvm/java-25-openjdk-arm64/bin/java -version
df -h /opt/photonvision
```

This script is scoped to our layout: service `photonvision.service`, destination `/opt/photonvision/photonvision.jar`, Java 25 installed at the path above, and a service already configured to use a compatible runtime. If `ExecStart` uses a different JAR path or incompatible Java, stop and ask a mentor. The existence of Java 25 alone does not mean the service uses it.

## 1. Copy the script and JAR to the Pi

**Run on your build computer, from this robot repository's root:**

```bash
RUBIK_HOST=10.10.106.72
scp utilities/rubik-pi/update-photonvision.sh "photon@$RUBIK_HOST:/home/photon/"
```

Set `RUBIK_HOST` to the current lab device address. Copy your selected build using its exact filename (see the [build guide](PHOTONVISION_BUILD.md)); alternatively, use Cockpit's Files application to upload it to `/home/photon`. Staging a file does not install it.

Log in:

```bash
ssh "photon@$RUBIK_HOST"
```

SSH may show a host-key fingerprint the first time. Verify it with the mentor/device owner. If a previously known host key changes, stop and investigate; do not disable host-key checking. Use your own approved login, not shared credentials pasted into this repository.

**Run on the Pi:**

```bash
chmod 750 ~/update-photonvision.sh
```

This lets the owner execute the script without making it world-writable. The script uses `sudo` when needed; read the command and understand its effect before entering your account password.

## 2. Preview the update

The following uses the filename from our experimental build as an example. Substitute the explicit filename of the build you intend to install:

```bash
JAR="$HOME/photonvision-dev-v2027.0.0-alpha-2-94-g51845e00-linuxarm64.jar"
~/update-photonvision.sh --dry-run "$JAR"
```

`--dry-run` checks the archive and prints the source, destination, and SHA-256 without stopping the service, installing files, or creating backups. The validator checks ZIP integrity, the PhotonVision entry point, Java class compatibility up to Java 25, and bundled Linux ARM64 native resources. It handles legitimate duplicate ZIP records produced by Shadow JAR packaging on the Pi's Python 3.12.

A **checksum** is a fingerprint of the file bytes. Compare the SHA-256 printed here to the checksum on the build computer. Matching checksums verify transfer integrity; they do not prove that the source is trustworthy or that the program will behave correctly.

## 3. Activate the approved build

After reviewing the dry-run and getting activation approval:

```bash
~/update-photonvision.sh "$JAR"
```

The script:

1. Validates the JAR before touching the service.
2. Uses a lock to prevent two updates running together.
3. Stages a complete copy beside the destination and compares its checksum.
4. Records the current service definition.
5. Stops PhotonVision so its database and JAR are not in use.
6. Backs up the entire application directory and a separate old JAR under `/opt/photonvision-backups/update-.../`.
7. Renames the staged copy into place atomically: the destination is never a half-copied file.
8. Starts PhotonVision and checks the HTTP status endpoint plus a stable process for five seconds.

Keep the printed backup path in your test notes. Backups are not automatically deleted; ask a mentor about retention when disk space becomes tight.

## 4. Verify robot-relevant behaviour

```bash
systemctl show photonvision -p ActiveState -p MainPID -p NRestarts
sudo journalctl -u photonvision --since '5 minutes ago' --no-pager
sha256sum /opt/photonvision/photonvision.jar
```

Open `http://10.10.106.72:5800/` (substitute the current address) and complete:

- [ ] Expected build/version shown; no restart loop or native-library load failure.
- [ ] Cameras and pipelines present; camera names still match robot code.
- [ ] Calibration and robot-to-camera measurements retained.
- [ ] Correct tag family and field layout, including Andymark/welded variant.
- [ ] Known tags decode to the correct IDs.
- [ ] Pose agrees with measured positions at several distances/angles.
- [ ] Robot NetworkTables/PhotonLib connection works; frame timestamps and latency are reasonable.
- [ ] Programming lead signs off before enabling vision-dependent robot behaviour.

A status-LED warning can be nonfatal, but inspect it rather than ignoring all errors. HTTP success alone does not test cameras, inference, or pose accuracy.

## Failure and rollback — mentor-managed

The script exits nonzero for validation/startup failures. If an error occurs after stopping, it attempts to start the currently installed JAR. **It does not automatically restore the old JAR/configuration**, because startup may have migrated the database. Report the error and backup path; do not keep retrying blindly. A failed new build may leave the new JAR installed or restart-looping.

For a rollback, a mentor should:

1. Stop the service and preserve the failed application's directory separately.
2. Inspect the chosen `photonvision-before.tar.gz` backup.
3. Restore the matched pre-update JAR **and configuration** from that backup, not just the standalone old JAR.
4. Check the saved `service-before.txt` and restore a compatible runtime configuration if necessary. This updater does not modify or automatically restore service definitions.
5. Start the service and repeat the camera/robot checks.

Do not use this script to downgrade across database versions without that review. It validates Java packaging, not native ABI requirements, PhotonLib compatibility, model accuracy, permissions across every OS image, or the safety of database migrations.

## Further reading

- [PhotonVision installation and normal update guidance](https://docs.photonvision.org/en/latest/docs/quick-start/quick-install.html)
- [Student source-build guide](PHOTONVISION_BUILD.md)
- [Updater source and local test instructions](../../utilities/rubik-pi/README.md)
