"""Offline negative controls for Galactifun release immutability.
This checks workflow policy, not historical-world/runtime certification.
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


class PolicyError(ValueError):
    pass


def verify_policy(build: str, release: str) -> None:
    """Fail closed if CI gains an automatic publisher or a mutable release path."""
    if not re.search(r"(?m)^permissions:\s*\n  contents: read\s*$", build):
        raise PolicyError("Normal build must use read-only repository permissions")
    if "actions/upload-artifact@v7" not in build:
        raise PolicyError("Normal build must retain its canonical raw JAR artifact")
    if "gh release " in build or "gh api repos/" in build:
        raise PolicyError("Normal build must never publish or mutate releases")
    if re.search(r"(?m)^  (?:push|pull_request|schedule|workflow_run):", release):
        raise PolicyError("Release workflow may be invoked manually only")
    if "  workflow_dispatch:" not in release:
        raise PolicyError("Release workflow needs explicit manual dispatch")
    if "type: boolean" not in release or "default: false" not in release:
        raise PolicyError("Release must require an explicit disabled-by-default publish input")
    if "if: inputs.publish == true" not in release:
        raise PolicyError("Release job must require affirmative publish input")
    if "--clobber" in release or "gh release delete-asset" in release or "gh release upload" in release:
        raise PolicyError("An existing release asset may never be rewritten")
    if "REQUESTED_TAG: ${{ inputs.tag }}" not in release:
        raise PolicyError("Requested tag must be passed as an environment value")
    if 'if [[ "$REQUESTED_TAG" != "$TAG" ]]' not in release:
        raise PolicyError("Release tag must match the exact JAR version")
    view = 'gh release view "$TAG"'
    create = 'gh release create "$TAG"'
    if view not in release or create not in release or release.index(view) > release.index(create):
        raise PolicyError("Existing-release existence guard must precede creation")
    if 'Refusing to modify the existing immutable release' not in release:
        raise PolicyError("Existing release must be rejected")


class ReleaseImmutabilityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.build = (ROOT / ".github/workflows/gradle.yml").read_text(encoding="utf-8")
        cls.release = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")

    def test_current_policy(self):
        verify_policy(self.build, self.release)

    def test_master_push_cannot_publish(self):
        with self.assertRaises(PolicyError):
            verify_policy(self.build + "\n      - run: gh release upload v1.0.7 x.jar\n", self.release)

    def test_normal_build_cannot_gain_write_permissions(self):
        with self.assertRaises(PolicyError):
            verify_policy(self.build.replace("contents: read", "contents: write"), self.release)

    def test_no_auto_release_tag_trigger(self):
        with self.assertRaises(PolicyError):
            verify_policy(self.build, self.release.replace("on:\n", "on:\n  push:\n    tags: ['v*']\n", 1))

    def test_no_clobber_or_asset_delete(self):
        for command in ("gh release upload --clobber", "gh release delete-asset"):
            with self.subTest(command=command):
                with self.assertRaises(PolicyError):
                    verify_policy(self.build, self.release + "\n" + command)

    def test_publish_is_explicit_not_implicit(self):
        with self.assertRaises(PolicyError):
            verify_policy(self.build, self.release.replace("if: inputs.publish == true", "if: always()"))

    def test_existing_release_is_a_terminal_blocker(self):
        with self.assertRaises(PolicyError):
            verify_policy(self.build, self.release.replace(
                'Refusing to modify the existing immutable release', 'Proceed with overwriting'))

    def test_tag_cannot_differ_from_artifact_version(self):
        with self.assertRaises(PolicyError):
            verify_policy(self.build, self.release.replace(
                'if [[ "$REQUESTED_TAG" != "$TAG" ]]', 'if [[ "$REQUESTED_TAG" == "" ]]'))

    def test_cannot_remove_publisher_existence_gate(self):
        with self.assertRaises(PolicyError):
            verify_policy(self.build, self.release.replace('gh release view "$TAG"', 'true'))

    def test_cannot_replace_raw_jar_with_unverified_sources(self):
        with self.assertRaises(PolicyError):
            verify_policy(self.build.replace("actions/upload-artifact@v7", "actions/download-artifact@v7"), self.release)


if __name__ == "__main__":
    unittest.main()
