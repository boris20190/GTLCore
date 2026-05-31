# Journal - boris (Part 1)

> AI development session journal
> Started: 2026-05-31

---



## Session 1: Bootstrap Trellis Guidelines

**Date**: 2026-05-31
**Task**: Bootstrap Trellis Guidelines
**Branch**: `gtl-1431`

### Summary

Initialized Trellis/Codex project workflow files and populated backend development guidelines from the current Forge/GTCEu codebase.

### Main Changes

- Added Trellis/Codex project workflow scaffolding and local agent instructions.
- Populated `.trellis/spec/backend/` with codebase-backed guidelines for directory structure, data/config conventions, error handling, logging, and quality checks.
- Updated and archived `00-bootstrap-guidelines` after completing the spec bootstrap checklist.

### Git Commits

| Hash | Message |
|------|---------|
| `b425d526` | (see git log) |

### Testing

- [OK] Confirmed `.trellis/spec/` has no remaining bootstrap placeholder text.
- [OK] Validated `00-bootstrap-guidelines` with `.trellis/scripts/task.py validate` before archiving.
- [INFO] Skipped Gradle checks because this session changed Trellis documentation/configuration only.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 2: Run Project Setup

**Date**: 2026-05-31
**Task**: Run Project Setup
**Branch**: `gtl-1431`

### Summary

Verified GTLCore local Gradle development setup, documented Java runtime requirements, and confirmed spotlessCheck, compileJava, and build pass with Java 21 running Gradle.

### Main Changes

- Verified local Java and Gradle wrapper availability for the Forge/Loom project.
- Confirmed the project builds with Java 21 running Gradle while preserving Java 17 bytecode output.
- Recorded the Java runtime metadata requirement in `.trellis/spec/backend/quality-guidelines.md`.
- Archived the `run-project-setup` Trellis task after documenting the build setup results.

### Git Commits

| Hash | Message |
|------|---------|
| `97247de8` | (see git log) |

### Testing

- [OK] `./gradlew spotlessCheck compileJava` passed with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`.
- [OK] `./gradlew build` passed and produced `build/libs/gtlcore-1.1.1.jar`.
- [INFO] Default Java 17 is executable, but its local JDK package is missing `$JAVA_HOME/release`, which slows/fails Loom source remap.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 3: Solid fuel generator multiblock

**Date**: 2026-05-31
**Task**: Solid fuel generator multiblock
**Branch**: `gtl-1431`

### Summary

Implemented HV/EV/IV wireless-only solid fuel generator multiblocks using solid steam boiler fuel semantics, boiler max-temperature throughput, exact remaining-EU accounting, recipes, lang entries, and spec/task records. Verified spotlessCheck and compileJava.

### Main Changes

- Switched the local working branch to `gtl-1431-skyblock` based on `origin/gtl-1431-skyblock`.
- Migrated the existing Trellis setup and solid fuel generator implementation onto the skyblock upstream baseline.
- Built `gtlcore-1.2.2.9-fix4.jar` and installed it into the PrismLauncher modpack instance.
- Removed the obsolete local `gtl-1431` branch after successful migration.

### Git Commits

| Hash | Message |
|------|---------|
| `bbd2ffef` | (see git log) |

### Testing

- [OK] `./gradlew spotlessCheck compileJava -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk`
- [OK] `./gradlew build -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk`
- [OK] User verified the PrismLauncher modpack loads successfully with `gtlcore-1.2.2.9-fix4.jar`.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 4: Solid fuel generator boiler parity

**Date**: 2026-05-31
**Task**: Solid fuel generator boiler parity
**Branch**: `gtl-1431`

### Summary

Aligned the solid fuel generator with GTCEu large boiler item-fuel semantics and basic steam turbine conversion, corrected boiler Kelvin display offset to 273.15, and fixed old-world loading by deferring migration markDirty.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `eff91f3d` | (see git log) |
| `d46a8989` | (see git log) |
| `dab084fa` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 5: Migrate GTLCore to skyblock baseline

**Date**: 2026-05-31
**Task**: Migrate GTLCore to skyblock baseline
**Branch**: `gtl-1431-skyblock`

### Summary

Switched local work to the gtl-1431-skyblock upstream baseline, migrated Trellis and solid fuel generator changes, built gtlcore-1.2.2.9-fix4.jar, installed it into the Prism modpack, and verified the modpack loads successfully.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `a2768ff2` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete
