import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile
import yaml

sys.dont_write_bytecode = True


def module(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


prepare = module('prepare_docker_env')
seal = module('finalize_demo')


class PrivateDeploymentTests(unittest.TestCase):
    def env(self, directory, with_rustfs=False):
        original = directory / 'cloud.yaml'
        original.write_text(yaml.safe_dump({'spring': {
            'datasource': {'dynamic': {'datasource': {'master': {
                'url': 'jdbc:mysql://mysql8:3306/test_original', 'username': 'test-user',
                'password': 'test-private-mysql-only'}}}},
            'data': {'redis': {'host': 'redis', 'password': 'test-private-redis-only', 'database': 3}}
        }}), encoding='utf-8')
        env = directory / 'private.env'
        with contextlib.redirect_stdout(io.StringIO()):
            prepare.prepare(original, env, with_freeswitch=True, with_rustfs=with_rustfs)
        return env, original

    def stage(self, directory):
        stage = directory / 'stage'
        for path in ['app/och-api.jar', 'web/index.html', 'docker/compose.yml', 'docker/Dockerfile',
                     'README.md', 'database/01-schema.sql', 'database/02-seed.sql',
                     'source/SHA256SUMS', 'docker/freeswitch/Dockerfile', 'docker/freeswitch/entrypoint.py',
                     'tools/DemoFreeSwitch.java', 'tools/DemoObjectStorage.java']:
            target = stage / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text('test fixture\n', encoding='utf-8')
        with zipfile.ZipFile(stage / 'source/OpenCallHub-public-source.zip', 'w') as archive:
            archive.writestr('README.md', 'public source fixture')
        return stage

    def test_preparation_isolates_databases_and_generates_distinct_secrets(self):
        with tempfile.TemporaryDirectory() as name:
            env, _ = self.env(Path(name))
            text = env.read_text()
            self.assertIn("OCH_DB_NAME='xhbt_demo'", text)
            self.assertIn("OCH_REDIS_DATABASE='10'", text)
            self.assertNotIn('/test_original', text)
            secrets = [line.split('=', 1)[1] for line in text.splitlines() if line.startswith('OCH_FS_') and ('PASSWORD' in line or 'SECRET' in line)]
            self.assertEqual(len(secrets), 4)
            self.assertEqual(len(set(secrets)), 4)

    def test_public_mode_still_rejects_env_files(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            stage = self.stage(root)
            (stage / 'docker/.env').write_text('test private')
            with self.assertRaisesRegex(ValueError, 'Runtime/private file'):
                seal.finalize(stage)

    def test_rustfs_private_delivery_keeps_the_existing_database_settings(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            stage = self.stage(root)
            env, original = self.env(root, with_rustfs=True)
            text = env.read_text()
            self.assertIn("OCH_RUSTFS_BUCKET='xhbt-demo-recordings'", text)
            self.assertIn("OCH_DB_PASSWORD='test-private-mysql-only'", text)
            with contextlib.redirect_stdout(io.StringIO()):
                seal.finalize(stage, original, env)
            with zipfile.ZipFile(next(root.glob('*Private*.zip'))) as bundle:
                self.assertEqual(bundle.read('stage/docker/.env'), env.read_bytes())

    def test_rustfs_access_key_is_rejected_outside_the_private_env(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            stage = self.stage(root)
            env, original = self.env(root, with_rustfs=True)
            access = next(line.split('=', 1)[1].strip("'") for line in env.read_text().splitlines()
                          if line.startswith('OCH_RUSTFS_ACCESS_KEY='))
            (stage / 'web/index.html').write_text(access)
            with self.assertRaisesRegex(ValueError, 'Private configuration value found'):
                seal.finalize(stage, original, env)
            self.assertFalse((stage / 'docker/.env').exists())

    def test_private_mode_includes_exactly_the_approved_env_and_seals_zip(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            stage = self.stage(root)
            env, original = self.env(root)
            with contextlib.redirect_stdout(io.StringIO()):
                seal.finalize(stage, original, env)
            archive = next(root.glob('*Private*.zip'))
            with zipfile.ZipFile(archive) as bundle:
                self.assertEqual(bundle.read('stage/docker/.env'), env.read_bytes())
                self.assertNotIn('test-private-mysql-only', bundle.read('stage/source/OpenCallHub-public-source.zip').decode('latin1'))
            report = json.loads((root / 'deployment-report.json').read_text())
            self.assertEqual(report['approved_private_files'], ['docker/.env'])
            self.assertEqual(report['secrets_outside_approved_paths'], 0)

    def test_private_mode_rejects_secret_leaks_in_nested_source_archives(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            stage = self.stage(root)
            env, original = self.env(root)
            with zipfile.ZipFile(stage / 'source/OpenCallHub-public-source.zip', 'w') as archive:
                archive.writestr('leak.txt', 'test-private-mysql-only')
            with self.assertRaisesRegex(ValueError, 'Private configuration value found'):
                seal.finalize(stage, original, env)
            self.assertFalse((stage / 'docker/.env').exists())
            self.assertFalse(list(root.glob('*Private*.zip')))


if __name__ == '__main__':
    unittest.main()
