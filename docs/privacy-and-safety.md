# Privacy And Safety

## Privacy

- HeadacheInsight is useful in local-only mode.
- Cloud analysis is opt-in and can be disabled at any time.
- Location is coarse, consent-based, and not tracked in the background.
- No ads, trackers, or third-party analytics SDKs are included.
- Sensitive data is redacted in logs wherever practical.

## Safety Language

- This app is not a medical device.
- This app does not diagnose, treat, or replace professional care.
- Use the app to track patterns and prepare for discussions with a clinician.

## Local Red Flags

The offline red-flag engine escalates when users report signs such as:

- sudden worst headache
- confusion or fainting
- fever or stiff neck
- one-sided weakness or numbness
- speech, vision, walking, or coordination problems
- severe vomiting
- recent head injury
- major change from usual pattern
- jaw pain or urgent visual concerns

On escalation, the app stops ordinary follow-up, shows an urgent safety screen, and stores a `RedFlagEvent`.
