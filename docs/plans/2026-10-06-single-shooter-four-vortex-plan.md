# Single shooter driven by four NEO Vortex motors

## Functional requirement and confirmed decisions
Nathan selected 1A, 2A, 3A: one shooter with four NEO Vortex motors on SPARK Flex controllers. CAN 22 is leader; CAN 23, 24, 25 follow CAN 22 without relative inversion. Feeder remains CAN 21. Preserve existing RPM targets, PID/feedforward, feeder logic and operator controls. Requested branch: feature/single-shooter-four-vortex. Commit and push that branch only; no deployment or merge.

## Current behaviour and inspection
Shooter.java currently instantiates two brushless SPARK Flex controllers. CAN 22 provides velocity closed-loop control and the readiness encoder. CAN 23 follows inverted. Startup configuration is persisted. Both controllers receive idle-mode changes, kill commands and disabled outputs. Telemetry covers both motors. Constants.java defines CAN 21–23. No tracked AGENTS.md or CLAUDE.md was found.

## Technical design
Add shooter CAN constants for 24 and 25. Keep motor 1 as the only velocity leader and readiness source. Configure motors 2–4 as non-inverted followers of 22 with native RPM encoder units and coast startup mode. Explicitly document that all four SPARK Flex controllers drive NEO Vortex motors on one mechanism. Extend brake/coast handling, kill handling and per-motor RPM/current telemetry to all four controllers. Ensure disabled output and runtime configuration changes do not break follower mode. Preserve existing public subsystem methods and all command/control bindings. Do not retune or introduce unrelated behaviour fixes.

## Objective acceptance criteria
- Four brushless SPARK Flex shooter controllers use distinct IDs 22, 23, 24, 25; feeder remains 21.
- Exactly one velocity control source: leader 22; three followers explicitly use relative inversion false.
- All four motors receive the intended idle-mode and stop behaviour; follower configuration survives runtime mode updates.
- Existing RPM targets, PID/feedforward, readiness logic, feeder behaviour and controls are unchanged.
- Telemetry exposes RPM and current for every shooter motor.
- Source diff is reviewed and git diff --check is run before commit. Build and tests are skipped at Nathan's explicit request; compilation and runtime behaviour remain unverified.
- Pushed feature branch SHA is verified against local HEAD; main is not modified remotely.

## Failure cases and risks
Non-inverted followers must match the mechanical coupling: wrong mounting/direction can make motors fight. CAN 24/25 must be provisioned and unique on the physical robot. Four motors increase electrical demand; existing power wiring and controller protection require mentor verification. No current-limit or tuning changes are included. A disconnected follower is not newly detected or used to inhibit feeding in this scoped change. Existing stopShooter/killShooter and idle-state semantics are preserved rather than redesigned. Software tests cannot establish safe motor direction or mechanical performance.

## Automated validation
Nathan explicitly requested changes without tests. Do not add or run tests for this change. Build is also not run. Review all changed motor lifecycle paths and the complete diff; run git diff --check as a whitespace check only. Source inspection is not a passing build or hardware validation.

## Physical validation (not performed; required before deployment)
With the mechanism safely restrained, no balls loaded, and people clear: confirm CAN IDs and motor/controller hardware, verify each motor's direction at low output and that all four assist the same shooter, inspect current sharing, verify disable/kill and brake/coast behaviour, then validate spin-up/readiness and feeder gating with mentor supervision. Record observations and outcomes here before approving deployment.

## Approval and open questions
Hardware choices are resolved. Nathan instructed the agent to proceed with implementation, commit and push to the new branch, then explicitly directed "just make the changes . No tests" instead of the proposed additional approval/testing workflow. Proceed under that task-specific instruction without further approval prompts. No deployment or merge is authorized.
