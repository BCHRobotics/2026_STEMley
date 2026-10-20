# Four-motor shooter tuning with a real stop

## Functional requirement
Nathan requested that shooter-pid-tuning incorporate "Updated shooter motor configuration to support inverted followers" and fix the stop behaviour. This is direct mentor authorization for local implementation; commit, push and deployment are not authorized.

## Current behaviour
The tuning commit eafb007 was based on main d30cb2c, with two motors. The requested title identifies commit 6b69fe30302cd60d46968ea2bd42b541f25a6156 on feature/single-shooter-four-vortex. Its successor adds unrelated comments, so it is excluded. The requested revision uses CAN 22 as leader and inverted followers 23/24/25, with feeder 21. Idle output previously commanded 1500 RPM and dashboard run state survived disable.

## Proposed technical implementation
Integrate the exact requested revision with an uncommitted merge, retaining the published tuning commit without rewriting history. Preserve tuning keys, gains, four-motor telemetry, CAN assignments and follower inversion. Stop removes leader and feeder output, clears shoot/reverse/feeder requests and resets the spin timer. The stopped path does not command idle RPM. Disable clears latched requests; re-enable alone must not restart dashboard shooting. Explicit feeder reverse remains available without flywheel motion. Kill also clears requests before applying the existing brake handling.

## Acceptance criteria
- One leader on CAN 22; inverted followers on CAN 23/24/25; feeder CAN 21.
- Tuning inputs and logging remain available, including all four motor RPM/current values.
- Stop leaves no powered flywheel/feeder request; flywheel coasts down.
- Disable clears latched tuning run state; re-enable alone does not resume.
- Explicit feeder reverse still works and Stop clears it.
- Normal shooting and kill state/output checks pass.
- Changes remain uncommitted/unpushed, with no robot deployment.

## Failure cases / edge cases
A physical flywheel does not stop instantly when output is removed. Inverted followers and CAN IDs need hardware verification; fake IO tests do not validate REV firmware follower behaviour or electrical safety. A newly issued normal command can restart shooting after re-enable by design. Existing readiness, input validation, live feeder-speed and tuning-gate review findings are outside this scoped fix.

## Validation plan
Run utilities/test_shooter_stop.py, which extracts and executes actual Java lifecycle/output methods with fake hardware IO. First confirm the existing stop commands idle velocity, then confirm the corrected normal/stop/disable/re-enable/reverse/kill paths. This is ad-hoc regression verification, not hardware simulation or a Gradle test suite. Attempt ./gradlew compileJava test in an isolated non-root build copy using an existing compatible JDK; report failures without installing tools or changing dependencies. Run git diff --check and inspect the integrated source and unresolved merge entries.

Physical validation remains required before mentor-approved deployment: no balls loaded, clear the mechanism, verify follower direction/current sharing at low output, then test Stop, disable/re-enable, feeder reverse and normal spin-up with supervision.

## Open questions
None for the requested local changes. Final history strategy/commit/push and deployment remain review gates.

## Evidence
The ad-hoc regression failed before the stop change with "Stop still commands an idle velocity" and passed after it. An isolated non-root Bubblewrap copy using existing JDK 21 ran the repository wrapper with `--no-daemon --max-workers=1 compileJava test`: BUILD SUCCESSFUL. Java source/target remain 17. `test NO-SOURCE` means no Gradle tests were discovered, not a passing suite. No physical test has been performed. The older 2026-10-06 plan is historical and its non-inverted-follower/no-tests statements do not describe this task.
