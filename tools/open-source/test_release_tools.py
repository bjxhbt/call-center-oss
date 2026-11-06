import importlib.util
import tempfile
import unittest
import io
import wave
from pathlib import Path


def module(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + ".py"))
    loaded = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(loaded)
    return loaded


metrics = module("evaluate_asr")
release = module("export_release")


def row(**overrides):
    result = {"file": "one.wav", "provider": "TENCENT", "source": "real_phone",
              "reference_text": "abcd", "hypothesis_text": "abXd", "keywords": "ab|cd",
              "first_text_latency_ms": "100", "final_text_latency_ms": "200", "result": "success"}
    return result | overrides


class MetricTests(unittest.TestCase):
    def test_failed_samples_count_as_deletions_in_weighted_cer(self):
        report = metrics.evaluate([row(), row(file="two.wav", reference_text="ab", result="failed")])
        group = report["groups"][0]
        self.assertEqual(group["cer"], 3 / 6)
        self.assertEqual(group["keyword_hit_rate"], .25)
        self.assertEqual(group["final_text_latency_ms"]["measured"], 1)

    def test_normalization_and_source_groups(self):
        report = metrics.evaluate([row(reference_text="Ａ b，c!", hypothesis_text="aBc"),
                                   row(file="mock.wav", provider="MOCK", source="mock")])
        self.assertEqual(len(report["groups"]), 2)
        self.assertEqual(next(g for g in report["groups"] if g["source"] == "real_phone")["cer"], 0)

    def test_rejects_mock_as_real_results_and_invalid_metrics(self):
        for example in (row(provider="MOCK"), row(first_text_latency_ms="nan"),
                        row(final_text_latency_ms="-1"), row(reference_text=""), row(provider=""), row(file="")):
            with self.assertRaises(ValueError):
                metrics.evaluate([example])
        with self.assertRaises(ValueError):
            metrics.evaluate([row(), row()])

    def test_keywords_are_normalized_unique_and_nonempty(self):
        group = metrics.evaluate([row(hypothesis_text='abcd', keywords='ab|\uff21\uff22|\uff0c|cd', result='partial')])["groups"][0]
        self.assertEqual(group['keyword_hit_rate'], 1)
        self.assertEqual(group['partials'], 1)


class ReleaseTests(unittest.TestCase):
    def test_generated_ring_is_deterministic_mono_pcm(self):
        data = release.synthetic_ring()
        self.assertEqual(data, release.synthetic_ring())
        with wave.open(io.BytesIO(data), "rb") as audio:
            self.assertEqual((audio.getnchannels(), audio.getsampwidth(), audio.getframerate(), audio.getnframes()),
                             (1, 2, 8000, 16000))

    def test_literal_credentials_are_reported_without_their_values(self):
        findings = release.scan_text('pass' 'word = "sensitive-value-456"\nsecret-key: "${S3_SECRET_KEY}"\n')
        self.assertEqual(len(findings), 1)
        self.assertNotIn("sensitive-value-456", str(findings))
        self.assertEqual(release.scan_text('-----BEGIN RSA PRIVATE KEY-----')[0]["kind"], "private-key")

    def test_source_containing_the_scanner_is_not_a_private_key(self):
        self.assertFalse(any(f["kind"] == "private-key" for f in release.scan_text(
            Path(release.__file__).read_text(encoding="utf-8"))))

    def test_unquoted_yaml_and_properties_credentials_are_reported(self):
        text = 'pass' 'word: sensitive-value\nspring.datasource.pass' 'word=another-value\napi-key: ${KEY}\n'
        self.assertEqual(len(release.scan_text(text)), 2)
        self.assertFalse(release.scan_text('pass' 'word: string,', scalar_config=False))

    def test_test_credentials_are_replaced_only_in_test_paths(self):
        text = 'pass' 'word: "private-fixture-value"'
        self.assertEqual(release.public_test_credentials('src/main/config.ts', text), text)
        sanitized = release.public_test_credentials('frontend/src/__tests__/sip.test.ts', text)
        self.assertNotIn('private-fixture-value', sanitized)
        self.assertFalse(release.scan_text(sanitized))

    def test_local_addresses_are_normalized_without_rewriting_public_urls(self):
        self.assertEqual(release.public_text('http://127.0.0.1:4320 https://github.com'),
                         'http://127.0.0.1:4320 https://github.com')
        self.assertEqual(release.public_text('172.32.1.1'), '172.32.1.1')

    def test_export_refuses_existing_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError, "new directory"):
                release.export(Path(temporary), Path(temporary))


if __name__ == "__main__":
    unittest.main()
