"""Pure-Python checks of the actual workflow code; no SDK, network or secrets.

Run: python3 -B -m unittest discover -s tests/ci-release-validation -v
"""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest
import urllib.error
import urllib.request
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / '.github/workflows/release.yml').read_text()
VERIFIER = textwrap.dedent(WORKFLOW.split('          # BEGIN RELEASE VERIFIER (also exercised by pure-Python tests)\n')[1]
                          .split('          # END RELEASE VERIFIER')[0])
NAMESPACE = {'__name__': 'workflow_test'}
exec(compile(VERIFIER, 'release.yml:verifier', 'exec'), NAMESPACE)
VERIFY = NAMESPACE['verify_artifacts']
ABIS = NAMESPACE['ABIS']
CERT = NAMESPACE['CERTIFICATE_SHA256']


class ArtifactTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.names = {'gallery-45-foss-release.apk': (45, None)}
        self.names.update({f'gallery-{45 + i}-foss-{abi}-release.apk': (45 + i, abi)
                           for i, abi in enumerate(ABIS, 1)})
        self.certificate = CERT
        self.package = 'org.fossifymigration.baizhu.gallery'
        self.version = '1.15.0'
        self.code_delta = 0
        self.debug = False
        for name, (_, abi) in self.names.items():
            self.write_apk(name, [abi] if abi else ABIS)

    def write_apk(self, name, abis, payload=b'native library'):
        with zipfile.ZipFile(self.directory / name, 'w') as archive:
            for abi in abis:
                archive.writestr(f'lib/{abi}/libtest.so', payload)

    def command(self, args, **kwargs):
        name = Path(args[-1]).name
        if Path(args[0]).name == 'apksigner':
            return f'Signer #1 certificate DN: CN=Release\nSigner #1 certificate SHA-256 digest: {self.certificate}\n'
        code, _ = self.names[name]
        return (f"package: name='{self.package}' versionCode='{code + self.code_delta}' versionName='{self.version}'\n"
                + ('application-debuggable\n' if self.debug else ''))

    def verify(self):
        with patch.object(subprocess, 'check_output', side_effect=self.command):
            VERIFY(self.directory, Path('/sdk/build-tools/36.0.0'), 45, '1.15.0')

    def test_valid_artifacts_and_checksums(self):
        self.verify()
        lines = (self.directory / 'SHA256SUMS').read_text().splitlines()
        self.assertEqual(len(lines), 5)
        for line in lines:
            digest, name = line.split('  ')
            self.assertEqual(digest, hashlib.sha256((self.directory / name).read_bytes()).hexdigest())

    def test_wrong_release_certificate(self):
        self.certificate = 'a' * 64
        with self.assertRaises(AssertionError):
            self.verify()

    def test_multiple_signers(self):
        self.certificate += f'\nSigner #2 certificate SHA-256 digest: {CERT}'
        with self.assertRaises(AssertionError):
            self.verify()

    def test_signature_tool_failure(self):
        with patch.object(subprocess, 'check_output', side_effect=subprocess.CalledProcessError(1, 'apksigner')):
            with self.assertRaises(subprocess.CalledProcessError):
                VERIFY(self.directory, Path('/sdk/build-tools/36.0.0'), 45, '1.15.0')

    def test_all_five_certificates_are_checked(self):
        original = self.command
        last = list(self.names)[-1]
        def command(args, **kwargs):
            result = original(args, **kwargs)
            return result.replace(CERT, 'b' * 64) if Path(args[-1]).name == last else result
        with patch.object(subprocess, 'check_output', side_effect=command):
            with self.assertRaises(AssertionError):
                VERIFY(self.directory, Path('/sdk/build-tools/36.0.0'), 45, '1.15.0')

    def test_wrong_identity_version_code_or_debug(self):
        for attr, value in [('package', 'org.fossify.gallery'), ('version', '1.15.1'),
                            ('code_delta', 1), ('debug', True)]:
            with self.subTest(attr=attr):
                previous = getattr(self, attr)
                setattr(self, attr, value)
                with self.assertRaises(AssertionError):
                    self.verify()
                setattr(self, attr, previous)

    def test_missing_extra_and_wrong_names(self):
        name = next(iter(self.names))
        original = self.directory / name
        renamed = self.directory / name.replace('release.apk', 'unsigned.apk')
        original.rename(renamed)
        with self.assertRaises(AssertionError):
            self.verify()
        renamed.rename(original)
        (self.directory / 'extra.apk').write_bytes(b'extra')
        with self.assertRaises(AssertionError):
            self.verify()
        (self.directory / 'extra.apk').unlink()
        original.unlink()
        with self.assertRaises(AssertionError):
            self.verify()

    def test_universal_must_contain_all_abis(self):
        self.write_apk('gallery-45-foss-release.apk', ABIS[:-1])
        with self.assertRaises(AssertionError):
            self.verify()

    def test_split_must_contain_only_its_abi(self):
        self.write_apk('gallery-46-foss-armeabi-v7a-release.apk', ABIS)
        with self.assertRaises(AssertionError):
            self.verify()

    def test_universal_must_match_native_bytes_in_splits(self):
        self.write_apk('gallery-45-foss-release.apk', ABIS, b'wrong library')
        with self.assertRaises(AssertionError):
            self.verify()


class PreflightTests(unittest.TestCase):
    CODE = textwrap.dedent(WORKFLOW.split("          python3 - <<'PY'\n", 1)[1].split('          PY', 1)[0])
    SHA = '1' * 40

    def run_preflight(self, tag='v1.15.0', refs=None, exists=False):
        if refs is None:
            refs = f'{self.SHA}\trefs/tags/v1.15.0\n'
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'output'
            env = {'RELEASE_TAG': tag, 'GITHUB_REPOSITORY': 'owner/repo',
                   'GITHUB_API_URL': 'https://api.example.invalid',
                   'GH_TOKEN': 'test-not-a-secret', 'GITHUB_OUTPUT': str(output)}
            def not_found(*args):
                raise urllib.error.HTTPError('test', 404, 'Not Found', {}, None)
            with patch.dict(os.environ, env), patch.object(subprocess, 'check_output', return_value=refs), \
                    patch.object(urllib.request, 'urlopen', side_effect=None if exists else not_found), \
                    patch('json.load', return_value={}):
                exec(compile(self.CODE, 'release.yml:preflight', 'exec'), {})
            return output.read_text()

    def test_resolves_lightweight_tag(self):
        self.assertEqual(self.run_preflight(), f'sha={self.SHA}\n')

    def test_resolves_annotated_tag_to_peeled_commit(self):
        refs = f'{"2" * 40}\trefs/tags/v1.15.0\n{self.SHA}\trefs/tags/v1.15.0^{{}}\n'
        self.assertEqual(self.run_preflight(refs=refs), f'sha={self.SHA}\n')

    def test_rejects_branch_or_missing_tag(self):
        with self.assertRaises(SystemExit):
            self.run_preflight(refs=f'{self.SHA}\trefs/heads/v1.15.0\n')

    def test_rejects_shell_input_and_non_versions(self):
        for tag in ('main', 'v1.15.0; echo unsafe', 'v1.15.0\n', '../v1.15.0'):
            with self.subTest(tag=tag), self.assertRaises(SystemExit):
                self.run_preflight(tag=tag)

    def test_rejects_existing_release(self):
        with self.assertRaises(SystemExit):
            self.run_preflight(exists=True)


class WorkflowTests(unittest.TestCase):
    def test_python_and_shell_syntax(self):
        for workflow in ('release.yml', 'validate-fastlane-metadata.yml'):
            text = (ROOT / '.github/workflows' / workflow).read_text()
            blocks = text.split('        run: |\n')[1:]
            for index, block in enumerate(blocks):
                lines = []
                for line in block.splitlines():
                    if line.strip() and not line.startswith('          '):
                        break
                    lines.append(line[10:])
                script = '\n'.join(lines) + '\n'
                subprocess.run(['bash', '-n'], input=script, text=True, check=True)
                if "python3 - <<'PY'\n" in script:
                    code = script.split("python3 - <<'PY'\n")[1].split('\nPY')[0]
                    compile(code, f'{workflow}:{index}', 'exec')

    def test_metadata_checks_current_source(self):
        text = (ROOT / '.github/workflows/validate-fastlane-metadata.yml').read_text()
        code = textwrap.dedent(text.split("          python3 - <<'PY'\n")[1].rsplit('          PY', 1)[0])
        previous = Path.cwd()
        try:
            os.chdir(ROOT)
            exec(compile(code, 'metadata.yml', 'exec'), {})
        finally:
            os.chdir(previous)


if __name__ == '__main__':
    unittest.main()
