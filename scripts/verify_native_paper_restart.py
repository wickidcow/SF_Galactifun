#!/usr/bin/env python3
"""Disposable two-boot Paper 26.3/Slimefun/Galactifun startup and saved-gate safety gate.

This checks actual server enable/restart and byte-exact stargates.yml retention.
It does NOT certify entity distance-despawn, player navigation, or real owner worlds.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import time
from urllib.request import Request, urlopen

PAPER_BUILD = 249
PAPER_SHA256 = "4c734aeb4da98845934af1e5481f0df7c80ad3ca50d272319ffe2b0e8a187c8a"
PAPER_API = "https://fill.papermc.io/v3/projects/paper/versions/26.3/builds"
USER_AGENT = "SlimefunLegacyAddonValidation/1.0 (https://github.com/wickidcow/SF_Galactifun)"
STARGATES_FIXTURE = (
    "# Preserve this owner's entire YAML exactly, including unknown keys and signed coordinates\n"
    "custom-owner: OriginalOwner\n"
    "gates:\n"
    "  ffffffff:\n"
    "    world: unloaded_planet\n"
    "    x: -2147483648\n"
    "    y: -64\n"
    "    z: 2147483647\n"
    "    custom: retain\n"
    "future:\n"
    "  enabled: false\n"
)


def download_pinned_paper(destination: Path) -> None:
    request = Request(PAPER_API, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=45) as response:
        builds = json.load(response)
    if not isinstance(builds, list):
        raise RuntimeError("Unexpected PaperMC build response")
    record = next((build for build in builds
                   if build.get("id") == PAPER_BUILD and build.get("channel") == "STABLE"), None)
    if not record:
        raise RuntimeError("Pinned Paper 26.3 stable build 249 not found")
    download = record["downloads"]["server:default"]
    if download["checksums"]["sha256"].lower() != PAPER_SHA256:
        raise RuntimeError("Pinned Paper server checksum changed")
    url = download["url"]
    if not url.startswith("https://fill-data.papermc.io/"):
        raise RuntimeError("Unrecognized PaperMC artifact host")
    request = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=120) as response, destination.open("wb") as output:
        shutil.copyfileobj(response, output)
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    if digest != PAPER_SHA256:
        raise RuntimeError(f"Downloaded Paper checksum mismatch: {digest}")


def compile_native_probe(root: Path, plugins: Path) -> None:
    """Build a CI-only JavaPlugin against the exact Paper 26.3 API."""
    api_paths = list(Path.home().glob(
        ".gradle/caches/modules-2/files-2.1/io.papermc.paper/paper-api/"
        "26.3.build.205-stable/*/paper-api-26.3.build.205-stable.jar"
    ))
    if len(api_paths) != 1:
        raise RuntimeError(f"Expected exactly one Paper 26.3 API JAR in Gradle cache: {api_paths}")
    classes = root / "probe-classes"
    classes.mkdir()
    source = Path("scripts/native_probe/NativeAlienProbe.java").resolve()
    # The Paper API exposes Kyori Adventure interfaces on JavaPlugin supertypes.
    # Include the resolved, read-only compile API dependency jars for the probe.
    adventure_jars = sorted(Path.home().glob(".gradle/caches/modules-2/files-2.1/net.kyori/*/*/*/*.jar"))
    if not adventure_jars:
        raise RuntimeError("Kyori Adventure Paper API compile dependencies were not resolved")
    classpath = os.pathsep.join([str(api_paths[0])] + [str(jar) for jar in adventure_jars])
    subprocess.run(
        ["javac", "--release", "21", "-encoding", "UTF-8",
         "-cp", classpath, "-d", str(classes), str(source)],
        check=True
    )
    (classes / "plugin.yml").write_text(
        "name: NativeAlienProbe\n"
        "main: test.galactifun.NativeAlienProbe\n"
        "version: '1.0'\n"
        "api-version: '1.21.11'\n"
        "depend: [Galactifun]\n",
        encoding="utf-8"
    )
    subprocess.run(["jar", "cf", str((plugins / "NativeAlienProbe.jar").resolve()), "."],
                   cwd=classes, check=True)


def run_server(workdir: Path, paper_jar: Path, evidence: Path, cycle: int) -> None:
    command = ["java", "-Xms512M", "-Xmx2G", "-jar", str(paper_jar), "nogui"]
    process = subprocess.Popen(
        command, cwd=workdir, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace", bufsize=1
    )
    lines: list[str] = []
    ready = threading.Event()
    def read_log() -> None:
        assert process.stdout is not None
        for line in process.stdout:
            lines.append(line)
            if "Done (" in line and "For help" in line:
                ready.set()
    log_reader = threading.Thread(target=read_log, daemon=True)
    log_reader.start()
    deadline = time.monotonic() + 180
    stop_sent = False
    ready_at = None
    try:
        while process.poll() is None and time.monotonic() < deadline:
            if ready.is_set() and ready_at is None:
                ready_at = time.monotonic()
            # Permit next-tick startup verification and async post-startup hooks.
            if ready_at is not None and time.monotonic() - ready_at >= 5 and not stop_sent:
                assert process.stdin is not None
                process.stdin.write("stop\n")
                process.stdin.flush()
                stop_sent = True
            time.sleep(0.25)
        if process.poll() is None:
            raise TimeoutError(f"Paper boot/restart cycle {cycle} did not complete within 180s")
        log_reader.join(timeout=10)
    finally:
        if process.poll() is None:
            process.kill()
            process.wait(timeout=15)
        evidence.mkdir(parents=True, exist_ok=True)
        (evidence / f"paper-cycle-{cycle}.log").write_text("".join(lines), encoding="utf-8")
    output = "".join(lines)
    if process.returncode != 0 or not ready.is_set():
        raise RuntimeError(f"Paper failed to start/stop on cycle {cycle}; tail:\n" + "".join(lines[-65:]))
    if "################# Galactifun" not in output:
        raise RuntimeError(f"Galactifun did not finish enabling on cycle {cycle}; tail:\n" + "".join(lines[-65:]))
    if "Error occurred while enabling Galactifun" in output:
        raise RuntimeError(f"Galactifun threw during enable on cycle {cycle}")
    if "NATIVE_ALIEN_POLICY_FAIL" in output or "NATIVE_ALIEN_POLICY_PASS" not in output:
        raise RuntimeError(f"Real native Martian/Titan King distance policy failed on cycle {cycle}; tail:\n" + "".join(lines[-75:]))
    print(f"Native Paper 26.3 cycle {cycle}: Galactifun enabled and stopped cleanly")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--plugin", type=Path, required=True)
    parser.add_argument("--core", type=Path, required=True)
    args = parser.parse_args()
    plugin, core = args.plugin.resolve(), args.core.resolve()
    for jar in (plugin, core):
        if not jar.is_file() or jar.stat().st_size < 1000:
            raise RuntimeError(f"Missing real plugin JAR: {jar}")
    evidence = Path("build/native-evidence").resolve()
    evidence.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="galactifun-paper263-native-") as folder:
        root = Path(folder)
        server_jar = root / "paper-26.3-249.jar"
        download_pinned_paper(server_jar)
        workdir = root / "server"
        plugins = workdir / "plugins"
        plugins.mkdir(parents=True)
        shutil.copyfile(plugin, plugins / "SF_Galactifun.jar")
        shutil.copyfile(core, plugins / "Slimefun-Legacy.jar")
        compile_native_probe(root, plugins)
        (workdir / "eula.txt").write_text("eula=true\n", encoding="ascii")
        (workdir / "server.properties").write_text(
            "online-mode=false\nserver-ip=127.0.0.1\n"
            "spawn-protection=0\nview-distance=3\nsimulation-distance=3\n"
            "level-type=minecraft:flat\nmax-players=1\n",
            encoding="ascii"
        )
        data = plugins / "Galactifun" / "stargates.yml"
        data.parent.mkdir()
        original = STARGATES_FIXTURE.encode("utf-8")
        data.write_bytes(original)
        for cycle in (1, 2):
            run_server(workdir, server_jar, evidence, cycle)
            if data.read_bytes() != original:
                raise AssertionError(f"Existing stargates.yml bytes changed on cycle {cycle}")
    print("PASS: native Paper 26.3 enabled Galactifun twice; original stargates.yml remains byte-exact")
    print("PASS: real native Martian and Titan King spawn with expected distance-despawn flags on both boots")
    print("LIMITATION: unloaded-chunk entity retention and long-range in-game player tests remain outstanding")


if __name__ == "__main__":
    main()
