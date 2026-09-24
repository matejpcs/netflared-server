# Paper compatibility

Netflared Server is maintained as three Paper compatibility branches:

- `paper/1.20.x` — Paper 1.20 through 1.20.6
- `paper/1.21.x` — Paper 1.21 through 1.21.11
- `paper/26.x` — Paper 26.1.1, 26.1.2 and 26.2

Before any release is published, the workflow requires every listed Paper version in all three compatibility families to build successfully. Only after all 22 builds pass are the three GitHub Releases created. A failure in any single version blocks all three releases. For historical Paper versions whose API artifacts are no longer resolvable, the workflow uses the nearest compatible Paper API baseline while still producing a JAR specifically labeled for the target server version.

Java targets follow Paper's runtime requirements: Java 17 for Paper 1.20.0–1.20.4, Java 21 for Paper 1.20.5–1.21.11, and Java 25 for Paper 26.1+. The plugin uses only Bukkit/Paper APIs shared by these versions, so no NMS or versioned CraftBukkit code is required.

Paper changed its API dependency format at 26.1: older versions use `<version>-R0.1-SNAPSHOT`, while 26.x uses `<minecraft-version>.build.<build>-<status>`. The workflow therefore resolves each 26.x line independently. Paper 26.1.1 is included for completeness even though 26.1.2 is the supported 26.1 patch line.

See the official Paper documentation for current Java requirements and dependency format.
