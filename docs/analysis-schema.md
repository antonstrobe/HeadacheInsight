# Analysis Schema

The backend returns a strict structured `AnalysisResponse` with:

- `schema_version`
- `analysis_id`
- `owner_type`
- `owner_id`
- `generated_at`
- `urgent_action`
- `user_summary`
- `clinician_summary`
- `hypotheses`
- `suspected_patterns`
- `next_questions`
- `suggested_tracking_fields`
- `suggested_attachments`
- `suggested_doctor_discussion_points`
- `suggested_specialists`
- `suggested_tests_or_evaluations`
- `self_care_general`
- `disclaimer`
- `needs_human_clinician_review`

All backend responses are validated against JSON Schema and mirrored by Pydantic models.
