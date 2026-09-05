<div align="center">

# 🪐 Galactifun Legacy

**Space exploration, planetary worlds, rockets, Stargates and astronomy for Slimefun Legacy.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-brightgreen.svg?style=for-the-badge&logo=minecraft)](https://papermc.io/)
[![Paper](https://img.shields.io/badge/Paper-26.2-blue.svg?style=for-the-badge)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg?style=for-the-badge&logo=openjdk)](https://adoptium.net/)
[![Slimefun Legacy](https://img.shields.io/badge/Slimefun%20Legacy-4.1.45-yellow.svg?style=for-the-badge)](https://github.com/wickidcow/Slimefun-Legacy)
[![License](https://img.shields.io/badge/License-GPLv3-blueviolet.svg?style=for-the-badge)](LICENSE)

</div>

---

## About this fork

Galactifun Legacy modernizes Galactifun for the current Albion/Slimefun Legacy stack while preserving the addon IDs, existing `world_galactifun_*` world names and established gameplay wherever possible.

**Primary target:**

- Paper **26.2** / Minecraft **1.21.11**
- Java **25**
- Slimefun Legacy **4.1.45**
- Purpur as a Paper-compatible secondary target
- Folia groundwork/detection is present, but **Galactifun 1.0 is not marked Folia-supported**

The release JAR is named **`SF_Galactifun1.0.0.jar`**.

---

## Highlights

### 🌌 Planetary worlds

Explore the Moon, Mars, Venus, Titan, Enceladus, Io, Earth orbit and the rest of Galactifun's solar-system content with custom gravity, atmospheres, hazards, resources and alien ecosystems.

The 1.0 modernization tracks worlds by stable name/UUID and can rebind them after an external world manager unloads or reloads them. Existing Galactifun world names are intentionally retained so upgrades do not orphan established planets.

### 🚀 Safer rockets

Rocket travel has been hardened for modern Paper servers:

- destination chunks are prepared asynchronously before launch;
- temporary plugin chunk tickets protect the landing transaction;
- passenger searches are limited to nearby entities instead of scanning an entire world;
- teleport completion is checked instead of assumed;
- stale launch locks recover automatically;
- the source rocket is removed before destination cargo is created, closing the historical two-copy launch window;
- breaking a launch pad returns the rocket, stored cargo and fuel;
- breaking the rocket directly also returns its stored cargo and fuel through Slimefun's normal drop path;
- malformed legacy rocket fuel metadata is recovered instead of interrupting launch-pad ticks or block breaks.

### 🌀 Stargates and travel security

The old unrestricted teleport metadata flag has been replaced with destination-bound, expiring travel authorization shared by rockets, Stargates and administrative world travel.

Stargate addresses are now stored in Galactifun's own persistent `stargates.yml` registry rather than relying on removed Slimefun BlockStorage internals.

### 🔒 Resource and world protection

- naturally generated mapped planetary resources cannot be piston-moved by default;
- vanilla Nether portals are blocked in Galactifun planet worlds unless explicitly enabled;
- external plugin teleports into planet worlds remain blocked by default so Multiverse portals cannot silently bypass rocket progression;
- Galactifun no longer mutates global Paper/Spigot configuration on startup.

### ⚡ Performance cleanup

- oxygen checks run only against players in relevant Galactifun worlds;
- world/alien simulation cadence is configurable;
- alien spawning is bounded by active players instead of scanning every loaded chunk for every species;
- loaded Galactifun aliens are indexed by planet, removing the old full-world living-entity count scan;
- rocket passenger lookup is localized;
- world reload tracking no longer depends only on live `World` object identity.

By default, each active player gets six alien spawn attempts per planet simulation tick within a two-chunk radius, and only already-loaded chunks are inspected. Both values are configurable under `aliens:`.

---

## Optional integrations

Galactifun does **not** hard-depend on a world manager. Optional integrations are detected centrally and remain safe when absent.

| Integration | Behavior |
|:---|:---|
| **Multiverse-Core** | Galactifun can attach to already-loaded worlds and safely rebind after unload/reload. |
| **Multiverse-Inventories** | Inventory/group transitions are left under Multiverse-Inventories control. |
| **Multiverse-Portals** | Planet entry is blocked by default; planet exit can be allowed by configuration. |
| **BentoBox** | Existing managed Earth/world setups are respected rather than silently replaced. |
| **Geyser / Floodgate** | Detected as optional compatibility integrations. |
| **Folia** | Detection groundwork exists, but 1.0 intentionally does not opt in as Folia-supported. |

Relevant defaults live under `integrations:` in `config.yml`.

### Folia status

Galactifun is a multi-world addon: it creates, loads, binds and manages dedicated planet worlds. Folia 26.2 still requires region/entity schedulers in place of the Bukkit scheduler and does not currently provide a safe drop-in path for the plugin-managed world lifecycle Galactifun depends on.

For that reason, this release does **not** set `folia-supported: true`. Doing so before the world lifecycle and all region-owned tasks are actually safe would make the plugin appear compatible while risking thread-context failures or world corruption. Paper/Purpur remain the supported 1.0 targets.

---

## Diagnostics

Server owners can run:

```text
/galactifun doctor
```

The doctor report shows the Galactifun, server, Java and Slimefun versions; registered/loaded planet counts; detected optional integrations; Multiverse travel policy; and obvious configuration/runtime warnings.

Useful administrative commands also include:

```text
/galactifun world <world>
/galactifun effects
/galactifun sealed
```

The main command aliases are `/gf` and `/galactic`.

---

## Configuration notes

Important 1.0 defaults include:

```yaml
worlds:
  earth-name: world
  create-missing-earth: false
  allow-nether-portals: false

aliens:
  max-per-player: 8
  spawn-attempts-per-player: 6
  spawn-radius-chunks: 2

integrations:
  multiverse:
    portals:
      allow-entry-to-planets: false
      allow-exit-from-planets: true

security:
  prevent-piston-mapped-block-moves: true

performance:
  oxygen-check-interval: 20
  world-tick-interval: 100
```

`create-missing-earth: false` is deliberate: Galactifun will not silently create a replacement Earth when a Multiverse, BentoBox or custom-world installation expects another plugin to own that world.

---

## Building

The project uses Gradle **9.4.1** and a Java **25** toolchain. The build intentionally compiles against an exact Slimefun Legacy JAR rather than accidentally resolving another Slimefun fork.

Place Slimefun Legacy in:

```text
lib/Slimefun-Legacy.jar
```

or provide it explicitly:

```bash
./gradlew clean build -PslimefunCoreJar=/path/to/Slimefun-Legacy4.1.45.jar
```

The finished JAR is written to:

```text
build/libs/SF_Galactifun1.0.0.jar
```

GitHub Actions downloads the exact Slimefun Legacy 4.1.45 release JAR automatically before CI/release builds.

---

## Credits

Galactifun exists because of the work of its original authors and contributors, including **Seggan**, **Mooy1**, **GallowsDove**, **ProfElements**, **Charmandiox9**, and the wider Slimefun community.

This Legacy modernization is maintained in the `wickidcow/SF_Galactifun` fork for current Slimefun Legacy servers.

---

## License

Galactifun is licensed under the **GNU General Public License v3.0**.
