# build jar for pack validation

## Goal

Build the current `gtl-1431-skyblock` branch into a mod jar so it can be manually tested in the modpack.

## What I Already Know

* The user requested a jar build after syncing upstream into `gtl-1431-skyblock`.
* The project uses Gradle wrapper and Architectury Loom.
* `build.gradle` configures `archivesName = gtlcore` and version `1.2.2.9-fix4`.

## Requirements

* Run the project build without source changes.
* Report the generated jar path.
* Preserve the current git worktree contents.

## Acceptance Criteria

* [x] Gradle build completes successfully.
* [x] The produced mod jar is identified under `build/libs`.

## Out of Scope

* No source code changes.
* No new commits or pushes.
* No automatic replacement in the modpack server.

## Technical Notes

* Initial build with the default runtime failed because `javac` was unavailable under the default Java 17 path.
* Successful command: `./gradlew build --no-daemon -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk`.
* Main jar: `build/libs/gtlcore-1.2.2.9-fix4.jar`.
