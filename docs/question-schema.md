# Question Schema

Question templates are versioned JSON objects with these fields:

- `id`
- `category`
- `stage`
- `prompt`
- `shortLabel`
- `helpText`
- `answerType`
- `options`
- `priority`
- `required`
- `skippable`
- `voiceAllowed`
- `visibleIf`
- `redFlagWeight`
- `exportToClinician`
- `aiEligible`

Question sources:

- `seed`
- `local_rule`
- `api`
- `manual`

Stages:

- `acute_fast`
- `acute_detail`
- `profile`
- `doctor`
- `attachments`
- `review`
