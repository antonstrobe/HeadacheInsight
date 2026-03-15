import json
from pathlib import Path


def test_schema_files_present_and_parse() -> None:
    schema_dir = Path(__file__).resolve().parents[1] / "schemas" / "v1"
    for filename in [
        "analysis_response.schema.json",
        "question_template_list.schema.json",
        "attachment_analysis.schema.json",
    ]:
        payload = json.loads((schema_dir / filename).read_text(encoding="utf-8"))
        assert isinstance(payload, dict)
        assert payload
