# AGENTS.md

## Project

This is an existing Minecraft Forge mod project.

Environment:

- Minecraft: 1.20.1

- Forge: 47.x

- Java: 17

- Gradle / ForgeGradle

- Mojang mappings

- Java path: `D:/Java17`

Do not recreate the project or download another Forge MDK.

Do not migrate to NeoForge or another Minecraft version unless explicitly requested.

## Development Rules

- Use APIs compatible with Minecraft 1.20.1 Forge only.

- Do not assume APIs from newer Minecraft versions.

- When uncertain about a Minecraft or Forge API, inspect the actual project dependencies, mappings, or source before implementing.

- Prefer standard Forge and vanilla APIs.

- Avoid Mixins unless they are genuinely necessary.

- Preserve the existing project structure and naming conventions.

## Client / Server

Keep client-only and server-safe code separated.

Client-only classes such as:

- `Minecraft`

- `Screen`

- rendering classes

- client events

must never be loaded on a dedicated server.

Game-state changes should normally be authoritative on the server.

For GUI-driven entity changes, use:

`Client GUI -> C2S packet -> Server validation -> Server-side modification -> Vanilla/Forge synchronization`

Never rely on client-only entity modifications for persistent state.

## Display Entity Editor

The main feature is a Display Entity editor.

Required behavior:

- Add a `Display Entity Editor` item.

- Right-clicking a block face with the item creates a `BlockDisplay` in the adjacent block space.

- The BlockDisplay uses the clicked block's `BlockState`.

- Default scale is `(1, 1, 1)`.

- Default placement must visually occupy the correct 1×1×1 block space.

- Right-clicking an existing BlockDisplay with the editor opens an editing GUI.

The GUI edits:

- Translation X/Y/Z

- Rotation X/Y/Z in degrees

- Scale X/Y/Z

The transformation must use the actual Minecraft 1.20.1 Display Entity API and correctly handle:

- `Display.BlockDisplay`

- `Transformation`

- `Vector3f`

- `Quaternionf`

Take particular care with Display Entity transformation origins, block offsets, rotation centers, and quaternion conversion.

Edited values must persist after closing the GUI and after saving/reloading the world.

## Validation

Server-side packet handling must validate:

- target entity exists

- target is a supported Display Entity

- player is within a reasonable distance

- all numeric values are finite

- scale values are greater than zero

- extreme values are rejected or clamped safely

Invalid input must not crash the game.

## Build

Use Java 17.

The project Gradle configuration should use:

`org.gradle.java.home=D:/Java17`

After making code changes, run:

`.\gradlew.bat build`

Fix compilation errors before finishing.

Do not report the task as complete unless the project builds successfully, unless the build is blocked by an external/environmental issue. If blocked, clearly state the exact blocker.

## Working Style

Before changing code:

1. Inspect the current repository structure.

2. Inspect existing registrations, package names, mod id, networking, and client setup.

3. Integrate with existing architecture instead of replacing it.

After changing code:

1. Review the diff.

2. Build the project.

3. Fix errors.

4. Summarize changed files and any remaining limitations.
