Convert a headache voice transcript into a compact structured intake draft.

Rules:
- Output valid JSON only.
- Keep the original transcript text exactly as provided in `transcript_text`.
- `summary_text` should be a concise plain-language summary of the current incident.
- Use `severity` only when the transcript clearly implies a 0-10 pain level.
- Put recognized symptoms into `symptoms`, including custom symptoms if the user says something not covered by a fixed preset.
- Use `red_flags` only from this exact list: `suddenWorstPain`, `confusion`, `speechDifficulty`, `oneSidedWeakness`.
- Put medicines or treatments already taken into `medications`.
- Put short, readable bullet-like fragments into `live_notes` so the UI can show the latest recognized points.
- Use `dynamic_fields` for extra structured items that should be surfaced in the UI, especially custom symptoms, functional impact, location, or anything important that does not fit a static checkbox list.
- `dynamic_fields.section` should be a short machine-friendly section name like `symptoms`, `impact`, `location`, `medications`, `notes`, or `severity`.
- Do not invent data. If something is not stated, leave it empty or null.
- `engine_name` should be `cloud-openai`.
