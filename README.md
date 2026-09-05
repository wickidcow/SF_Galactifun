# 🪐 Galactifun Legacy

**Space exploration, planetary worlds, rockets, life support, alien ecosystems, and astronomy for Slimefun Legacy.**

Galactifun turns a Slimefun server into a small solar-system sandbox. Players can build aerospace equipment, launch into space, survive hostile planetary environments, explore generated alien worlds, work with atmospheric resources, and encounter extraterrestrial life without installing a client mod.

This repository is the **Slimefun Legacy maintenance fork** of Galactifun. The goal is to preserve the original addon and its gameplay while keeping it usable on the modern Paper stack used by Slimefun Legacy.

## ✨ What it adds

- 🌌 **Planetary exploration** — Earth orbit, the Moon, Mars, Venus, Titan, Enceladus, Io, Europa, and other celestial content.
- 🚀 **Rockets and launch systems** — aerospace progression, launch equipment, fuel systems, and interplanetary travel.
- 🧑‍🚀 **Life support** — spacesuits, oxygen management, atmospheric hazards, temperature, pressure, radiation, and gravity effects.
- ⚙️ **Slimefun machinery** — aerospace manufacturing, material processing, atmospheric harvesting, power generation, and advanced components.
- 👾 **Alien ecosystems** — custom extraterrestrial creatures and planetary spawning rules.
- 🌀 **Space travel systems** — planetary teleportation and stargate-style progression integrated with Galactifun worlds.
- 🪐 **Custom world generation** — each enabled planetary world keeps its own terrain generator and environmental rules.

## ✅ Slimefun Legacy 1.0.1 target

| Component | Target |
| --- | --- |
| Server | **Paper 26.2** primary; Purpur on the same Paper line supported |
| Java runtime/build | **Java 25** recommended |
| Plugin bytecode | **Java 21** |
| Slimefun | **Slimefun Legacy** primary runtime |
| Client | Vanilla Minecraft client; no client mod required |
| Optional world manager | **Multiverse-Core** soft dependency |
| Optional island platform | **BentoBox** soft dependency |

The 1.0.1 build performs startup compatibility checks for the server platform, Java runtime, required Slimefun provider/API linkage, known plugin conflicts, Folia detection, and optional Multiverse-Core/BentoBox presence. After startup it also verifies the registered planetary-world set.

### Multiverse-Core compatibility

Galactifun remains the owner of its planetary generators. Multiverse-Core is detected as an optional integration and is never hard-linked, so a Multiverse API change does not prevent Galactifun from loading. Generator lookup also falls back to the Galactifun world registry when an external world manager asks for a generator by world name before Bukkit resolves that world.

## 📦 Installation

1. Install **Slimefun Legacy** and start the server once.
2. Stop the server normally.
3. Place `SF_Glactifun1.0.1.jar` in the `plugins` folder.
4. Start the server and review the Galactifun compatibility preflight in the console.
5. Confirm the enabled planetary worlds load successfully before opening the server to players.

Do not use `/reload` to install or replace Galactifun. Restart the server normally.

## 🎮 Commands

The main command is:

```text
/galactifun <subcommand>
```

Aliases:

```text
/gf
/galactic
```

Available subcommands include Galactiport, alien spawn/removal, structure tools, sealing tools, and effects utilities. Access depends on the permissions provided by the addon and your server permission setup.

## 🔨 Building

Release/CI builds compile against the current `wickidcow/Slimefun-Legacy` master rather than relying only on an old standalone Slimefun dependency.

```bash
./gradlew clean build --no-daemon -PslimefunCoreJar=/path/to/Slimefun-Legacy.jar
```

Expected release artifact:

```text
build/libs/SF_Glactifun1.0.1.jar
```

The build uses Java 25 while deliberately emitting Java 21 bytecode for Galactifun-owned classes.

## ❤️ Credits and project lineage

Galactifun exists because of the work of its original authors and community contributors. This maintenance fork preserves that lineage rather than presenting the project as a new addon.

- **Seggan** — original creator
- **Mooy1** — core contributor
- **GallowsDove** — design and lore
- **ProfElements** — mechanics
- **Charmandiox9** — modern 1.21-era maintenance and InfinityLib refactoring
- **Slimefun Addon Community contributors** — historical development and maintenance
- **wickidcow** — Slimefun Legacy maintenance fork

## 📄 License

Galactifun is licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE).

---

**Unofficial community maintenance fork.** This project is not an official release of the original Slimefun project, Mojang Studios, or Microsoft.
