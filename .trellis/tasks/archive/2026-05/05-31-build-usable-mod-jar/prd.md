# Build usable mod jar

## Goal

Build a usable GTLCore mod jar that can be copied into a Minecraft Forge 1.20.1 modpack.

## What I already know

- The user wants a final jar for modpack use.
- This task should not change business logic.
- The project uses Gradle with Architectury Loom and Forge.
- `gradle.properties` sets `archives_base_name=gtlcore`.
- The user's modpack includes `gtladditions`, which requires `gtlcore` version `1.2.2.9` or above.
- After the version metadata fix, the modpack loads `gtlcore-1.2.2.9.jar` but fails during `gtladditions` common setup because `gtladditions-3.1Custom_SubSpace-fix3.jar` directly accesses `ConfigHolder.INSTANCE.enableSkyBlokeMode`.
- `gtladditions-3.1Custom_SubSpace-fix3.jar` was built against `origin/gtl-1431-skyblock`, not plain `origin/gtl-1431`.
- `enableSkyBlokeMode`, `MEPatternBufferPartMachineBase`, and other GTLCore APIs referenced by GTL Additions exist on `origin/gtl-1431-skyblock`.
- Project guidelines recommend Java 21 as the Gradle runtime while compiling Java 17 bytecode.

## Requirements

- Use `origin/gtl-1431-skyblock` as the upstream baseline instead of patching plain `gtl-1431`.
- Preserve the local Trellis/bootstrap and solid fuel generator commits by applying them onto the skyblock baseline.
- Run the Gradle build with a complete local JDK.
- Produce the remapped mod jar under `build/libs/`.
- Copy the final jar into the PrismLauncher instance and disable the old GTLCore jar to avoid duplicate mod ids.
- Report the final artifact path and any build issues.

## Acceptance Criteria

- [x] `./gradlew build` completes successfully.
- [x] A non-sources, non-dev jar exists in `build/libs/`.
- [x] The jar metadata declares the skyblock branch version `1.2.2.9-fix4`.
- [x] Static compatibility scan finds no missing GTLCore class references from `gtladditions`.
- [x] The final response identifies the jar file copied into the modpack.

## Out of Scope

- Business feature development.
- Version number changes.
- Publishing to a Maven repository or pushing to remote.

## Technical Notes

- Use `-Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk` if the shell Java runtime is incomplete.
- `build.gradle` configures Loom, `withSourcesJar()`, and standard jar manifest metadata.
