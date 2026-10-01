# Maintenance contract

Target Minecraft/Paper 1.21.11+ and Java 21 bytecode. Prioritize Paper/Purpur and preserve existing Folia/Leaf scheduling boundaries.

Keep gate addresses, gfsgAddress, world names, full signed coordinates, recipes and all existing item/research IDs. Loading stargates.yml must succeed before any gameplay/world initialization. Missing data is fresh only when the path is truly absent; malformed/unreadable files are not empty registries.

Preserve unknown saved keys and use the original YAML format. Serialize fully before staged replacement; retain file symlinks and existing POSIX modes. Do not claim atomic replacement protects against all power loss or concurrent external writers. Run the full project tests, supported-version compilation and real startup/restart checks. Test-only libraries must not enter the plugin JAR.

Use scoped branches, preserve concurrent work, and record exact tested commits and remaining limitations. Do not publish a stable release or bump versions before coordinated validation. Produce raw installable JARs, with the aggregate bundle managed in Slimefun-Legacy.
