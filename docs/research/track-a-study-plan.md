# Track A research / pilot preparation

**Observed sample: n=0.** No interview, willingness-to-pay response or external beta session has been conducted.
No participant has been contacted by the coding agent. This preparation does not mark PRE-005, BACK-009, BACK-010,
LATER-004 or LATER-005 as completed. Existing task dependencies and team review still apply.

## Interview protocol (PRE-005)

Target 3–5 voluntary people who regularly choose their own everyday outfits. A smaller observed sample must be reported as such.
Before recording, explain purpose, optional questions, storage/deletion window and withdrawal; request separate permission for quotes.
Use anonymous study IDs. Keep consent contact mapping privately, never in this repo. No weight, measurements or body photographs are needed.

Ask about a recent concrete occasion: how they chose clothes, what slowed them down, what they actually wore and why.
Ask what visual suggestions would help, which labels they would trust, what they would correct, and what would make them stop using the app.
Ask comfort with garment photos and optional body photos separately; do not imply body photos are required to manage a wardrobe.
Record counterexamples and privacy objections, not only supportive remarks. Do not infer time saved from stated preference.

## Supervised pilot (BACK-009)

Prerequisites: reviewed build/hash, B security/quota gate, privacy/delete flow and declared release mode. Start with up to five voluntary users;
one or two supervised sessions are acceptable evidence of those sessions only. Do not promise live VTO when its mode has not passed.

Tasks: add a front-only garment; add a verified front/back pair; correct a label; find by category/color; replace just the back;
cancel deletion; delete a test item; reopen the app. Profile/outfit/try-on tasks are included only after the corresponding track is integrated.
Observe success without help, success with help, failure, duration, error/recovery and a short participant explanation.
Install/consent counts, app sessions and task counts must be real counts, not invitation counts. No background analytics is implied by this protocol.

## Analysis (BACK-010)

Use one row per participant/task in a private study file: anonymous ID, build, task, start/end, outcome, assistance, issue, opt-out.
Report denominators, missing observations, median/range for observed times, exact counts for failures and quoted evidence only with permission.
Compare people/builds only where the design supports it. No causal time-saving, fit-accuracy, retention or population claims from this small sample.
Current result: **NOT RUN; zero observations; no product validation claim.**

## Localization / market decision (LATER-004)

Decision now: defer rollout beyond Vietnamese until pilot evidence and a language reviewer exist. No foreign-user sample or market validation is claimed.
A future VN → one-market memo must specify one segment, climate/occasion/unit/date differences, verified competitor/source dates,
acquisition hypothesis and support/translation cost. A translated UI alone is insufficient evidence. Preserve the Vietnamese baseline meanwhile.

## Monetization (LATER-005)

No payment, affiliate, commerce or paid resource is provisioned. Actual provider cost and usage data are unavailable.
Use `tools/research/cost_model.py` for explicit-input sensitivity calculations; default/example rates are hypothetical, never provider quotes.
Collect 3–5 real, consented responses about paying for a concrete task, include refusal and current alternatives, avoid leading purchase questions.
Decision now: no commercialization claim; first measure cost and usefulness under a hard budget cap chosen by the team.
