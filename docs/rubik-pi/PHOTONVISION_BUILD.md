# Build PhotonVision from source for the Rubik Pi 3

## Learning goal

Learn how source code becomes a deployable application, and why the **target hardware** matters even when Java is involved. You will clone PhotonVision, choose a version, prepare its build tools, compile a Linux ARM64 fat JAR, and stage that file on the Rubik Pi. Activation is a separate mentor-approved step.

This guide uses a Linux build computer (Debian 13 / Ubuntu-style examples). We used **aibox** as the build host; you do not need that particular computer. Check available disk space, permissions and internet access with a mentor before installing tools. Windows/macOS users should follow upstream's host-specific instructions rather than assuming Linux cross-compilation commands work unchanged.

## Important: two different Java projects

| Project | Runs on | Purpose |
|---|---|---|
| `BCHRobotics/2026_STEMley` | roboRIO | Robot controls, WPILib 2026 / Java 17 |
| `PhotonVision/photonvision` | Rubik Pi | Camera processing application; inspected 2027 development source requires Java 25 |

Do not clone PhotonVision inside the robot project's `src/`, change the robot's Java toolchain to match it, or deploy its JAR to the roboRIO.

**2027 development PhotonVision must not be assumed compatible with the robot's 2026 PhotonLib.** Confirm the protocol/library combination and a recovery plan with a mentor before activation. Stable season-compatible releases remain the default for competition.

## Vocabulary

- **Repository / clone:** Git's versioned source history / a local copy of it.
- **Branch:** A line of development; `main` can change as upstream work is merged.
- **Commit:** A specific source snapshot identified by a hash. Record it for reproducibility.
- **JDK:** Java Development Kit, which includes a compiler. A runtime/JRE runs Java but is not the full development toolkit.
- **Gradle wrapper (`gradlew`):** The project's launcher for its pinned build system; use it instead of installing an arbitrary global Gradle version.
- **Node and pnpm:** Tools that build the browser interface and download its dependencies.
- **Cross-compilation:** Building native machine code for a different platform. A Linux x86-64 build host can produce Linux ARM64 libraries for the Rubik.
- **Fat/shadow JAR:** The application plus the dependencies/resources it needs, rather than a small library-only archive.

## 1. Choose source before building

Read the [official build guide](https://docs.photonvision.org/en/latest/docs/contributing/building-photon.html) and the [release notes](https://github.com/PhotonVision/photonvision/releases). Select one of:

- **Stable tag:** A named release approved for the season. Its prerequisites may differ from today's `main`.
- **Pinned commit:** A reviewed exact source snapshot; best for repeatable comparisons.
- **Current `main`:** Useful for development, not a guarantee of competition readiness or inclusion of an unmerged PR.

The normal walkthrough below clones `main`, then records its commit. It does not merge experimental code automatically.

## 2. Check and prepare the build computer

**Run on the build computer:**

```bash
hostname
java -version
javac -version
printf 'JAVA_HOME=%s\n' "${JAVA_HOME:-not set}"
node --version
pnpm --version
gcc --version
g++ --version
```

For the source snapshot we inspected:

| Tool | Requirement / recommendation | Why |
|---|---|---|
| Git | Available command-line client | Clone source and identify commits |
| JDK | Java 25 | Required by the inspected Gradle Java toolchain/plugins |
| Node.js | **24.x recommended** | `photon-client/package.json` declares 24.x; upstream prose says 24+ |
| pnpm | 11.x; follow the checkout's configured version | Frontend dependency manager |
| GCC / G++ | GCC 14 used by upstream Linux packaging | Native compiler/linker |
| ARM64 toolchain | Project-provided installer | Builds ARM64 native components |
| Gradle | Use the included wrapper | Correct project-specific version |
| Python / uv | Not needed for the selected application-JAR command | Used for other tasks, including PhotonLibPy; not YOLO training in this workflow |

Our build succeeded on Node 25.9.0 but emitted an engine warning; **prefer Node 24**, which matches the repository, for students. Tool requirements can change: recheck `shared/common.gradle`, `photon-client/package.json`, `photon-server/build.gradle`, and `.github/workflows/build.yml` in the chosen checkout.

### Linux package example — installation needs approval

For a Debian 13 / compatible Ubuntu build host where these packages are available:

```bash
sudo apt update
sudo apt-get --simulate install git openjdk-25-jdk build-essential
sudo apt install git openjdk-25-jdk build-essential
```

If the machine is already logged in as root, `sudo` is unnecessary. Do not run an OS upgrade or add arbitrary third-party repositories to resolve a missing package; ask a mentor for the appropriate JDK distribution.

Set Java for **this build shell**, without changing the robot project's Java environment:

```bash
# Example for an x86-64 Debian/Ubuntu build computer, not the ARM64 Pi:
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/java" -version
```

On another host/architecture, use its actual JDK 25 path. Gradle can use `JAVA_HOME` even when `java -version` on PATH shows a different installation: check both.

Install Node 24 and pnpm using their [official Node](https://nodejs.org/en/download) and [pnpm](https://pnpm.io/installation) instructions. If your approved build account **already has nvm**, this is a user-scoped option:

```bash
nvm install 24
nvm use 24
npm install --global pnpm@11
```

These commands install tools. Do not use `sudo npm`, change a shared server's global Node version, or download/run an unknown installer without approval. An existing pnpm 11 installation can be reused; Gradle may provision its checkout-configured pnpm version as well.

## 3. Clone the source separately

**Run on the build computer:**

```bash
mkdir -p "$HOME/frc-tools"
cd "$HOME/frc-tools"
git clone --branch main https://github.com/PhotonVision/photonvision.git
cd photonvision
git status --short --branch
git rev-parse HEAD
```

Save the commit hash in your test notes. If the destination already exists, inspect it rather than deleting it or cloning over it. To update an existing clean `main` checkout, use `git pull --ff-only`; do not discard uncommitted work or silently switch from a team's experimental branch.

For an approved stable release, select that tag with `git switch --detach TAG_NAME` (replace `TAG_NAME` with the actual release tag). Recheck that tag's prerequisites. For a pinned historical baseline, our normal dev build used commit:

```text
47b879e27d5d698be10a6542e853b943b0dc2020
```

Selecting a commit is not approval to install it. A detached checkout is fine for an unmodified build; create a separate branch before editing source.

## 4. Install project dependencies and ARM64 tools

**In the PhotonVision source directory on the build computer:**

```bash
./gradlew installArm64Toolchain
(cd photon-client && pnpm install)
```

The toolchain installer downloads development tools and writes local caches. `pnpm install` downloads frontend dependencies; the subshell `( ... )` returns you to the project root afterward. The first build needs internet access for Gradle, Maven and frontend packages; this is not a promise of an offline build.

The `photon-server:shadowJar` task also wires in frontend dependency installation and UI building. The explicit frontend step is helpful to diagnose setup issues, not a separate manual copying requirement.

## 5. Compile the Rubik application

```bash
./gradlew photon-targeting:jar photon-server:shadowJar -PArchOverride=linuxarm64 --console=plain
```

| Part | What it does |
|---|---|
| `photon-targeting:jar` | Builds the Java targeting library needed by the application |
| `photon-server:shadowJar` | Compiles/packages the application, web UI, resources and dependencies |
| `-PArchOverride=linuxarm64` | Selects the Pi's Linux ARM64 native libraries instead of the host's architecture |
| `--console=plain` | Makes terminal output easier to read/save |

Wait for `BUILD SUCCESSFUL`; inspect any warnings. Output is in:

```text
photon-server/build/libs/photonvision-<version>-linuxarm64.jar
```

`<version>` is explanatory notation, not a literal filename. Old builds can coexist in that folder. Choose the exact output corresponding to the recorded commit.

Packaging success is **not** a test-suite result. Run mentor-selected tests separately on the build host (native tests need host-native libraries):

```bash
./gradlew photon-core:test --tests '*AprilTag*' --console=plain
```

Read the report and native-loader warnings; tests can pass without exercising every backend. Full test suites, hardware inference, pose accuracy and robot integration are separate checks. Do not use `-PArchOverride=linuxarm64` to run ARM64 native tests on an x86-64 machine.

## 6. Stage the exact artifact and verify transfer

Use Bash on the build computer. Replace `YOUR_EXACT_BUILD` with the filename printed by your build:

```bash
RUBIK_HOST=10.10.106.72
JAR="$PWD/photon-server/build/libs/YOUR_EXACT_BUILD-linuxarm64.jar"
test -f "$JAR" && sha256sum "$JAR"
```

If the file check fails, stop and correct the filename. Then:

```bash
scp "$JAR" "photon@$RUBIK_HOST:/home/photon/"
ssh "photon@$RUBIK_HOST"
```

**Inside that SSH session, now on the Rubik:**

```bash
sha256sum "$HOME/YOUR_EXACT_BUILD-linuxarm64.jar"
```

Compare the full checksums. Stage the updater from this robot repository if needed, then follow [Updating PhotonVision](PHOTONVISION_UPDATE.md). Merely copying the JAR does not change the running version. Do not use a wildcard when several builds exist.

## 7. Verify the target runtime before activation

Current-main Java 25 class files cannot run on Java 17. On the Pi:

```bash
systemctl cat photonvision
java -version
```

Check the exact executable in `ExecStart`: `java -version` shows the system default, which can differ from the service's Java. Also check Ubuntu/glibc/native dependencies and hardware support with a mentor. The JAR extension and ARM64 filename alone do not prove compatibility.

### Mentor-only Java 25 setup example

Our Ubuntu 24.04 Rubik originally used Java 17. We installed Java 25 alongside it, retained Java 17 as the default `java` command, and changed **only PhotonVision** with a service drop-in. These are reviewed installation instructions, not an automatic student prerequisite:

```bash
oldjava=$(readlink -f /usr/bin/java)
sudo apt-get --simulate install --no-install-recommends openjdk-25-jre-headless
# Review the simulation and get approval before continuing:
sudo apt-get install --no-install-recommends openjdk-25-jre-headless
sudo update-alternatives --set java "$oldjava"
/usr/lib/jvm/java-25-openjdk-arm64/bin/java -version
```

This selects the saved `java` default explicitly; package installation can also register other Java commands. It does not remove Java 17. Check your actual paths before proceeding.

Inspect existing overrides first. With mentor approval, create a PhotonVision-specific override using `sudo systemctl edit photonvision`, preserving unrelated settings:

```ini
[Service]
ExecStart=
ExecStart=/usr/lib/jvm/java-25-openjdk-arm64/bin/java -Xmx512m -jar /opt/photonvision/photonvision.jar
```

The empty `ExecStart=` clears the old command before assigning the replacement. Save it, then `sudo systemctl daemon-reload` makes systemd reread configuration. **Do not restart an older build solely to test this change**; coordinate activation and a full backup with the mentor. Our helper script does not create this override or install Java.

## Troubleshooting

| Symptom | Likely issue / next check |
|---|---|
| Gradle says it uses Java 21/17 | Check `JAVA_HOME`, not just PATH's `java`; use the approved JDK 25 shell |
| `UnsupportedClassVersionError` on Pi | The service runtime is older than the compiled classes |
| Frontend engine warning | Match Node 24.x to the checked-out package metadata |
| Native library missing | Wrong target, missing toolchain/package, or unsupported OS ABI; ask a mentor, don't suppress the error |
| `Strip for arm64 was not found. Skipping` | Build may still succeed without size-stripping; it is not proof all native tests passed |
| JAR lacks web UI or wrong filename | Use the fat `shadowJar` output, not a library JAR |
| Main build lacks ML AprilTag toggle | An unmerged PR is not automatically part of `main`; see appendix |
| Service starts but no camera/robot data | Check camera configs, season-compatible PhotonLib, calibration, networking and field layout |

## Mentor-only appendix: experimental ML AprilTag PR #2604

**Not the normal student/competition path.** At the time of our work, [PR #2604](https://github.com/PhotonVision/photonvision/pull/2604) was draft/unmerged. Verify its current status before experimenting.

The design uses an object-detection model on the Rubik's NPU to find candidate AprilTag **regions of interest (ROIs)**. Traditional CPU AprilTag detection then decodes those cropped regions for IDs/corners. It narrows the search area; AI does not replace tag decoding or automatically guarantee accurate pose.

Work in a dedicated PhotonVision testing branch, never the robot code branch:

```bash
git status --short --branch
git fetch origin pull/2604/head:pr-2604-ml-apriltag
git rev-parse pr-2604-ml-apriltag
git switch -c rubik-ml-apriltag-2604
git merge --no-ff pr-2604-ml-apriltag
```

The PR ref can advance: record its exact SHA. **Do not copy these commands as a conflict-resolution recipe.** Our merge conflicted because `main` had renamed configuration APIs and reverted static cropping. The mentor resolved that by retaining current `ConfigProvider`, preserving main's frame-provider behaviour, and restoring only the crop helper/tests needed for ML ROIs—not the removed static-crop feature. Do not choose "ours" or "theirs" for every file, or delete failing tests just to get a green build.

If Git reports conflicts, stop for review. For a clean starting tree, `git merge --abort` can return to the pre-merge branch state. Resolve/review/test before committing or building. A newer PR/main pair may need different resolutions.

### Recorded experimental build evidence

| Evidence | Value |
|---|---|
| Main baseline | `47b879e27d5d698be10a6542e853b943b0dc2020` |
| PR head used | `a8328a6bd32a4bbb7f42e4c81369c9a976ad233f` |
| Local merge commit | `51845e007e4134299fe5fe037d2fcbb043c1695d` |
| Artifact | `photonvision-dev-v2027.0.0-alpha-2-94-g51845e00-linuxarm64.jar` |
| SHA-256 | `29af406bab93dcd094cc6cc3eeec70edd245db3af96f5fc701e1fae67f42a873` |
| Targeted tests | 19 passed (crop and AprilTag-related); native-loader warning present |
| Packaged resources | ML pipeline plus `models/apriltagV4-yolo11.tflite` |

The merge commit exists in our local PhotonVision checkout; it was **not published to upstream GitHub** and is not automatically fetchable from that repository. These identifiers document the experiment, not a self-contained distribution of its source or JAR. This robot-repository support branch does not include PhotonVision binaries/models or all the experimental source changes.

Before activating an experimental build: verify model loading and NPU execution on the actual camera, compare traditional/ML detection at near/far distances, test no-tag/fallback cases and frame-edge crops, compare pose against known ground truth, and check latency/false negatives. The draft feature has reported near-tag performance and ROI-visualization concerns. Our targeted tests/build did not establish NPU accuracy or autonomous safety.

## Build/test record template

```text
Source repository / branch:
Source commit / optional PR head:
JDK / Node / pnpm / host OS:
Build command and result:
Tests and warnings:
Artifact filename / SHA-256:
Pi address / runtime:
Backup path:
Camera, pose and robot-connection results:
Mentor approving activation:
```

Sources: [PhotonVision build documentation](https://docs.photonvision.org/en/latest/docs/contributing/building-photon.html), [upstream packaging workflow](https://github.com/PhotonVision/photonvision/blob/main/.github/workflows/build.yml), [PR #2604](https://github.com/PhotonVision/photonvision/pull/2604).
