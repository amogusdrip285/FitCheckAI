import json
import os
from pathlib import Path
import stat
import tempfile
import unittest

from poc_runner import (
    _PNG_1X1_BASE64,
    BudgetPolicy,
    FakeVtoProviderAdapter,
    FashnProviderAdapter,
    OpenAiImageEditProviderAdapter,
    PocHarnessRunner,
    PocValidationError,
    apply_negative_mutation,
    build_manifest_from_private_folder,
    inspect_local_image_file,
    purge_subject_private_outputs,
    sanitize_diagnostic,
)


class PocRunnerTest(unittest.TestCase):
    def setUp(self) -> None:
        manifest_path = Path(__file__).parent / "fixtures" / "technical_fixtures_manifest.json"
        self.manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.positive_by_id = {c["caseId"]: c for c in self.manifest["positiveCases"]}
        self.temp_dir = tempfile.TemporaryDirectory()
        self.private_out = Path(self.temp_dir.name) / "private_outputs"

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_positive_cases_track_independent_pair_and_sequential_multi_garment(self) -> None:
        runner = PocHarnessRunner(
            adapter=FakeVtoProviderAdapter(simulated_latency_ms=0),
            budget=BudgetPolicy(max_attempts=20),
            private_output_dir=self.private_out,
        )

        # Case 1: Single TOP
        rec1 = runner.run_case(self.positive_by_id["case-pos-01-single-top"])
        self.assertEqual(rec1.overall_status, "SUCCEEDED")
        self.assertEqual(rec1.pair_execution_mode, "TWO_INDEPENDENT_REQUESTS")
        self.assertEqual(rec1.garment_application_mode, "SINGLE_GARMENT")
        self.assertEqual(rec1.applied_item_ids, ["item-top-01"])
        self.assertEqual(rec1.unapplied_item_ids, [])
        self.assertEqual(len(rec1.attempts), 2)
        self.assertEqual(rec1.rubric_template["qualityStatus"], "UNREVIEWED")

        # Private directory and files must have strict permissions (0700 / 0600)
        dir_mode = stat.S_IMODE(os.stat(self.private_out).st_mode)
        self.assertEqual(dir_mode, 0o700)
        first_file = self.private_out / rec1.attempts[0].private_output_file
        self.assertTrue(first_file.exists())
        self.assertEqual(stat.S_IMODE(os.stat(first_file).st_mode), 0o600)

        # Case 3: Multi-garment TOP + BOTTOM + SHOES (SHOES excluded, sequential drift check flagged)
        rec3 = runner.run_case(self.positive_by_id["case-pos-03-top-bottom-shoes"])
        self.assertEqual(rec3.overall_status, "SUCCEEDED")
        self.assertEqual(rec3.garment_application_mode, "SEQUENTIAL_MULTI_GARMENT")
        self.assertTrue(rec3.requires_sequential_drift_check)
        self.assertEqual(rec3.applied_item_ids, ["item-top-02", "item-bottom-01"])
        self.assertEqual(rec3.unapplied_item_ids, ["item-shoes-01"])
        self.assertEqual(len(rec3.attempts), 4)  # 2 views * 2 garment passes

    def test_all_six_negative_fixtures_are_rejected_before_any_provider_call(self) -> None:
        runner = PocHarnessRunner(
            adapter=FakeVtoProviderAdapter(simulated_latency_ms=0),
            budget=BudgetPolicy(max_attempts=20),
            private_output_dir=self.private_out,
        )

        for neg in self.manifest["negativeCases"]:
            base = self.positive_by_id[neg["basePositiveCaseId"]]
            mutated = apply_negative_mutation(base, neg["mutation"], neg["caseId"])
            rec = runner.run_case(mutated)
            self.assertEqual(rec.overall_status, "REJECTED_PRE_UPLOAD", msg=neg["caseId"])
            self.assertEqual(rec.rejection_code, neg["expectedErrorCode"], msg=neg["caseId"])
            self.assertEqual(len(rec.attempts), 0, msg=f"Must not call provider for {neg['caseId']}")

    def test_partial_back_failure_never_claims_dual_complete_and_records_failed_attempt(self) -> None:
        runner = PocHarnessRunner(
            adapter=FakeVtoProviderAdapter(fail_views={"BACK"}, simulated_latency_ms=0),
            budget=BudgetPolicy(max_attempts=10),
            private_output_dir=self.private_out,
        )
        rec = runner.run_case(self.positive_by_id["case-pos-02-single-dress"])
        self.assertEqual(rec.overall_status, "PARTIAL")
        self.assertEqual(len(rec.attempts), 2)
        self.assertEqual(rec.attempts[0].status, "SUCCEEDED")
        self.assertEqual(rec.attempts[1].status, "FAILED")
        self.assertTrue(any("Partial completion" in w for w in rec.warnings))

    def test_live_provider_is_strictly_blocked_without_budget_consent_and_pre001(self) -> None:
        live_adapter = FashnProviderAdapter(api_key="dummy-never-used")

        # Blocked when allow_live_calls is False
        runner_disabled = PocHarnessRunner(
            adapter=live_adapter,
            budget=BudgetPolicy(allow_live_calls=False),
            private_output_dir=self.private_out,
        )
        rec = runner_disabled.run_case(self.positive_by_id["case-pos-01-single-top"], manifest_eligible_for_live=True)
        self.assertEqual(rec.overall_status, "REJECTED_PRE_UPLOAD")
        self.assertEqual(rec.rejection_code, "FORBIDDEN")

        # Blocked when synthetic manifest (eligibleForLiveQualityGate=False) is passed to live adapter
        runner_no_real_data = PocHarnessRunner(
            adapter=live_adapter,
            budget=BudgetPolicy(allow_live_calls=True, pre001_hackathon_cleared=True, approved_budget_usd=2.0),
            private_output_dir=self.private_out,
        )
        rec2 = runner_no_real_data.run_case(
            self.positive_by_id["case-pos-01-single-top"], manifest_eligible_for_live=False
        )
        self.assertEqual(rec2.overall_status, "REJECTED_PRE_UPLOAD")
        self.assertEqual(rec2.rejection_code, "CONSENT_REQUIRED")

    def test_attempt_cap_and_kill_switch_and_redaction(self) -> None:
        # Attempt cap stops mid-run and records BLOCKED attempt
        runner = PocHarnessRunner(
            adapter=FakeVtoProviderAdapter(simulated_latency_ms=0),
            budget=BudgetPolicy(max_attempts=1),
            private_output_dir=self.private_out,
        )
        rec = runner.run_case(self.positive_by_id["case-pos-01-single-top"])
        self.assertEqual(rec.overall_status, "PARTIAL")
        self.assertEqual(rec.attempts[0].status, "SUCCEEDED")
        self.assertEqual(rec.attempts[1].status, "BLOCKED")
        self.assertEqual(rec.attempts[1].error_code, "BUDGET_EXCEEDED")

        # Purge helper deletes private files
        removed = purge_subject_private_outputs(self.private_out, "case-pos-01")
        self.assertEqual(removed, 1)

        # Redaction strips base64, URLs, and paths
        raw = "Failed on data:image/png;base64,AAAA1111== from https://cdn.fashn.ai/secret/img.png at /home/user/body.jpg"
        clean = sanitize_diagnostic(raw)
        self.assertNotIn("AAAA1111", clean)
        self.assertNotIn("cdn.fashn.ai", clean)
        self.assertNotIn("/home/user", clean)

    def test_private_folder_ingestion_and_repo_boundary_guard(self) -> None:
        import base64

        private_in = Path(self.temp_dir.name) / "user_private_photos"
        private_in.mkdir(parents=True, exist_ok=True)
        png_raw = base64.b64decode(_PNG_1X1_BASE64)
        (private_in / "person_front.png").write_bytes(png_raw + b"PERSON_FRONT_REAL_LOCAL")
        (private_in / "top_front.png").write_bytes(png_raw + b"GARMENT_FRONT_REAL_LOCAL")

        # Rejects placing private photos inside the Git repository
        with self.assertRaises(PocValidationError) as ctx:
            build_manifest_from_private_folder(
                private_dir=Path(__file__).parent,
                provider_id="fake-provider",
                consent_confirmed=True,
            )
        self.assertEqual(ctx.exception.code, "FORBIDDEN")

        # Accepts external private folder, inspects PNG header/dimensions/hash, and runs preflight
        manifest = build_manifest_from_private_folder(
            private_dir=private_in,
            provider_id="fake-provider",
            consent_confirmed=True,
            garment_category="TOP",
        )
        self.assertTrue(manifest["eligibleForLiveQualityGate"])
        case = manifest["positiveCases"][0]
        self.assertEqual(case["requestedViews"], ["FRONT"])
        self.assertEqual(case["personImages"]["front"]["mimeType"], "image/png")

        runner = PocHarnessRunner(
            adapter=FakeVtoProviderAdapter(simulated_latency_ms=0),
            budget=BudgetPolicy(max_attempts=5),
            private_output_dir=self.private_out,
            private_input_dir=private_in,
        )
        rec = runner.run_case(case, manifest_eligible_for_live=True)
        self.assertEqual(rec.overall_status, "SUCCEEDED")
        self.assertEqual(rec.pair_execution_mode, "SINGLE_VIEW_FRONT")

    def test_openai_adapter_offline_mock_exif_jpeg_and_live_safety_gates(self) -> None:
        # 1. Retired models are strictly rejected
        for retired in ("dall-e-2", "dall-e-3", "gpt-image-1"):
            with self.assertRaises(PocValidationError) as ctx:
                OpenAiImageEditProviderAdapter(model_name=retired, offline_mock=True)
            self.assertEqual(ctx.exception.code, "UNSUPPORTED_CAPABILITY")

        # 2. Build private folder with rotated EXIF Orientation=6 JPEG (1024x768 sensor -> 768x1024 upright)
        private_in = Path(self.temp_dir.name) / "vto_input_openai"
        private_in.mkdir(parents=True, exist_ok=True)
        app1 = (
            b"Exif\x00\x00"
            + b"II*\x00"
            + (8).to_bytes(4, "little")
            + (1).to_bytes(2, "little")
            + (0x0112).to_bytes(2, "little")
            + (3).to_bytes(2, "little")
            + (1).to_bytes(4, "little")
            + (6).to_bytes(2, "little")
            + (0).to_bytes(2, "little")
            + (0).to_bytes(4, "little")
        )
        sof0 = b"\xff\xc0\x00\x11\x08" + (768).to_bytes(2, "big") + (1024).to_bytes(2, "big") + b"\x03\x01\x11\x00\x02\x11\x00\x03\x11\x00"
        sos_eoi = b"\xff\xda\x00\x0c\x03\x01\x00\x02\x11\x03\x11\x00\x3f\x00\x00\xff\xd9"
        jpeg_bytes = b"\xff\xd8\xff\xe1" + (len(app1) + 2).to_bytes(2, "big") + app1 + sof0 + sos_eoi

        (private_in / "person_front.jpg").write_bytes(jpeg_bytes)
        (private_in / "garment_front.jpg").write_bytes(jpeg_bytes + b"\x00")

        inspected = inspect_local_image_file(private_in / "person_front.jpg")
        self.assertEqual(inspected["mimeType"], "image/jpeg")
        self.assertEqual(inspected["exifOrientation"], 6)
        self.assertEqual(inspected["width"], 768)
        self.assertEqual(inspected["height"], 1024)

        # Corrupted file rejected
        bad_file = private_in / "corrupt.bin"
        bad_file.write_bytes(b"NOT_A_VALID_IMAGE_AT_ALL_0123456789")
        with self.assertRaises(PocValidationError) as bad_ctx:
            inspect_local_image_file(bad_file)
        self.assertEqual(bad_ctx.exception.code, "INVALID_REQUEST")

        # 3. Offline OpenAI Mock executes full multipart schema check with $0.00 spend
        manifest = build_manifest_from_private_folder(
            private_dir=private_in,
            provider_id="openai",
            consent_confirmed=True,
            garment_category="TOP",
        )
        case = manifest["positiveCases"][0]

        mock_adapter = OpenAiImageEditProviderAdapter(
            model_name="gpt-image-2.6-sunburst",
            offline_mock=True,
        )
        runner = PocHarnessRunner(
            adapter=mock_adapter,
            budget=BudgetPolicy(max_attempts=5, cost_per_credit_usd=1.0),
            private_output_dir=self.private_out,
            private_input_dir=private_in,
        )
        rec = runner.run_case(case, manifest_eligible_for_live=True)
        self.assertEqual(rec.overall_status, "SUCCEEDED")
        self.assertEqual(rec.total_cost_usd, 0.0)
        self.assertEqual(rec.attempts[0].source, "FAKE")
        self.assertEqual(mock_adapter.call_count, 1)
        self.assertEqual(mock_adapter.last_multipart_summary["imageCount"], 2)
        self.assertFalse(mock_adapter.last_multipart_summary["hasInputFidelity"])
        self.assertFalse(mock_adapter.last_multipart_summary["hasForbiddenResponseFormat"])

        # 4. Live OpenAI adapter is strictly blocked when allow_live_calls=False
        live_openai = OpenAiImageEditProviderAdapter(
            api_key="sk-never-sent-live-key-1234567890",
            model_name="gpt-image-2",
            offline_mock=False,
        )
        blocked_runner = PocHarnessRunner(
            adapter=live_openai,
            budget=BudgetPolicy(allow_live_calls=False, cost_per_credit_usd=1.0),
            private_output_dir=self.private_out,
            private_input_dir=private_in,
        )
        blocked_rec = blocked_runner.run_case(case, manifest_eligible_for_live=True)
        self.assertEqual(blocked_rec.overall_status, "REJECTED_PRE_UPLOAD")
        self.assertEqual(blocked_rec.rejection_code, "FORBIDDEN")
        self.assertEqual(live_openai.call_count, 0)

        # 5. Fault mapping: insufficient_quota, invalid_api_key (redacted), and network_timeout (0 retries)
        for scenario, expected_status, expected_code in (
            ("insufficient_quota", "FAILED", "INSUFFICIENT_QUOTA"),
            ("invalid_api_key", "FAILED", "UNAUTHORIZED"),
            ("empty_data", "FAILED", "INVALID_AI_OUTPUT"),
            ("network_timeout", "TIMED_OUT", "UPSTREAM_TIMEOUT"),
        ):
            fault_adapter = OpenAiImageEditProviderAdapter(
                model_name="gpt-image-1.5",
                offline_mock=True,
                mock_scenario=scenario,
            )
            fault_runner = PocHarnessRunner(
                adapter=fault_adapter,
                budget=BudgetPolicy(max_attempts=5, cost_per_credit_usd=1.0),
                private_output_dir=self.private_out,
                private_input_dir=private_in,
            )
            fault_rec = fault_runner.run_case(case, manifest_eligible_for_live=True)
            self.assertEqual(fault_rec.overall_status, "FAILED")
            self.assertEqual(fault_rec.attempts[0].status, expected_status)
            self.assertEqual(fault_rec.attempts[0].error_code, expected_code)
            self.assertNotIn("sk-secret", fault_rec.attempts[0].error_message or "")
            self.assertEqual(fault_adapter.call_count, 1)
            self.assertTrue(fault_adapter.last_multipart_summary["hasInputFidelity"])


if __name__ == "__main__":
    unittest.main()
