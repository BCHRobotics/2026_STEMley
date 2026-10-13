# Rubik Pi 3: student support guides

The Rubik Pi is a **coprocessor**: a separate computer that runs camera processing so the roboRIO can concentrate on controlling the robot. PhotonVision is the camera application. Cockpit is a web-based way to manage the Pi's files, services, and logs.

## Start here

| Your goal | Guide |
|---|---|
| Understand and safely install a prepared PhotonVision JAR | [Updating PhotonVision](PHOTONVISION_UPDATE.md) |
| Open Cockpit or understand its Ubuntu/backports installation | [Cockpit management](COCKPIT.md) |
| Clone PhotonVision source and compile a Linux ARM64 JAR | [Building PhotonVision](PHOTONVISION_BUILD.md) |
| Find the updater script and its isolated tests | [Rubik utilities](../../utilities/rubik-pi/README.md) |

## Which computer does what?

| Computer | Job | Important distinction |
|---|---|---|
| Build computer (for example, the team's Linux build host) | Compiles source into a JAR | Has development tools such as a JDK, Node, pnpm, and C++ compilers |
| Rubik Pi 3 | Runs PhotonVision and provides Cockpit | Needs the correct Java runtime and hardware libraries |
| roboRIO | Runs the team's robot program | Uses WPILib/PhotonLib; it is **not** the destination for the PhotonVision application JAR |
| Student laptop | Opens web interfaces or SSH sessions | A command typed inside SSH executes on the Pi, not the laptop |

The robot project remains WPILib 2026 / Java 17. A Java 25 requirement in these guides refers to the separately built PhotonVision **2027 development application**, not a request to upgrade the robot project's Java version.

## Reference lab device

These are convenient examples, not permanent robot-network settings:

- SSH account: `photon`
- Lab address: `10.12.86.xx`
- PhotonVision: `http://10.12.86.xx:5800/`
- Cockpit: `https://10.12.86.xx:9090/`
- PhotonVision service: `photonvision.service`
- Installed application: `/opt/photonvision/photonvision.jar`
- Configuration: `/opt/photonvision/photonvision_config/`

Confirm the current address.

## FRC safety and sign-off

1. Work on a bench or disabled robot. Tell the driver/programming lead before interrupting vision.
2. Use a mentor-approved version. Stable releases are the default for competition; development builds and unmerged PRs are experiments.
3. Verify PhotonLib compatibility before connecting a new PhotonVision build to 2026 robot code. A 2027 dev JAR is **not automatically compatible** with the 2026 season's PhotonLib.
4. Record the source commit, artifact checksum, backup path, test results, and who approved activation.
5. Check camera calibration, camera names, robot-to-camera transforms, and the exact AprilTag field layout. The robot code and coprocessor must agree on the same layout variant.
6. A successful build or HTTP response does not prove accurate pose estimation. Compare known tag IDs and known physical positions before relying on vision in autonomous operation.

These guides add support tools and documentation only. They do not change robot code, install packages automatically, or deploy to the roboRIO.
