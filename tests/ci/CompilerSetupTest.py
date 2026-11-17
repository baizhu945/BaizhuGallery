import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import tomllib
import unittest

REPO = Path(__file__).resolve().parents[2]


class CompilerSetupTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / '.github/scripts').mkdir(parents=True)
        (self.root / 'gradle').mkdir()
        self.script = self.root / '.github/scripts/setup-kotlin.sh'
        shutil.copyfile(REPO / '.github/scripts/setup-kotlin.sh', self.script)
        shutil.copyfile(REPO / '.github/kotlin-compiler.sha256', self.root / '.github/kotlin-compiler.sha256')
        shutil.copyfile(REPO / 'gradle/libs.versions.toml', self.root / 'gradle/libs.versions.toml')

    def run_setup(self, *arguments):
        env = {key: value for key, value in os.environ.items() if key not in ('RUNNER_TEMP', 'GITHUB_PATH')}
        return subprocess.run(['bash', str(self.script), *arguments], env=env, text=True, capture_output=True)

    def manifest(self, value):
        (self.root / '.github/kotlin-compiler.sha256').write_text(value)

    def test_catalog_and_archive_pin_agree(self):
        result = self.run_setup('--validate-only')
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_rejects_invalid_digest(self):
        archive = (self.root / '.github/kotlin-compiler.sha256').read_text().split()[1]
        self.manifest(f'not-a-sha256  {archive}\n')
        self.assertNotEqual(self.run_setup('--validate-only').returncode, 0)

    def test_rejects_different_compiler_version(self):
        self.manifest('0' * 64 + '  kotlin-compiler-0.0.0.zip\n')
        self.assertNotEqual(self.run_setup('--validate-only').returncode, 0)

    def test_rejects_extra_manifest_fields(self):
        original = (self.root / '.github/kotlin-compiler.sha256').read_text().strip()
        self.manifest(original + ' trailing-field\n')
        self.assertNotEqual(self.run_setup('--validate-only').returncode, 0)

    def test_refuses_install_outside_ephemeral_runner(self):
        result = self.run_setup()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('ephemeral GitHub runner', result.stderr)


class BuildEnvironmentTest(unittest.TestCase):
    def test_workflows_install_current_supported_sdk(self):
        versions = tomllib.loads((REPO / 'gradle/libs.versions.toml').read_text())['versions']
        sdk = versions['app-build-compileSDKVersion']
        java = versions['app-build-javaVersion'].removeprefix('VERSION_')
        for filename in ('pr.yml', 'release.yml'):
            with self.subTest(workflow=filename):
                workflow = (REPO / '.github/workflows' / filename).read_text()
                packages = re.findall(r"packages: ['\"]([^'\"]+)['\"]", workflow)
                self.assertEqual(len(packages), 1)
                installed = set(packages[0].split())
                self.assertEqual(installed, {'platform-tools', f'platforms;android-{sdk}', 'build-tools;36.0.0'})
                self.assertIn(f"java-version: '{java}'", workflow)
                self.assertIn('runs-on: ubuntu-24.04', workflow)
                self.assertIn('--max-workers=2', workflow)
                self.assertIn('-Dorg.gradle.jvmargs=-Xmx4096m', workflow)
                self.assertIn('-Pandroid.builder.sdkDownload=false', workflow)

    def test_signing_is_not_exposed_to_pull_requests(self):
        workflow = (REPO / '.github/workflows/pr.yml').read_text()
        self.assertNotIn('pull_request_target', workflow)
        self.assertNotIn('secrets.', workflow)
        self.assertIn('contents: read', workflow)
        self.assertIn('persist-credentials: false', workflow)


if __name__ == '__main__':
    unittest.main()
