# Evidence record template

Copy one record per executed case; do not replace NOT RUN with PASS without an actual run.

| Field | Value |
|---|---|
| Case / card / owner | |
| Commit / uncommitted build description | |
| APK path, SHA-256, build variant | |
| Device model / API / physical or emulator | |
| Release mode | CORE / FRONT / DUAL |
| Expected data source | MANUAL / FAKE / RULES / AI_LIVE |
| Preconditions and dataset item IDs | |
| Steps, one per action | |
| Expected result, covering every acceptance clause | |
| Actual result | |
| Outcome | NOT RUN / PASS / FAIL / BLOCKED |
| Evidence files / provider request IDs | |
| Bug severity / owning track | |
| Limitations, missing devices/consumers | |

Core matrix: Room CRUD/reopen, Profile optional photo, front/back ownership, recognition consent/manual merge/timeouts,
1–5 outfit validity/exact selection/stale IDs, media delete/retry, offline/lifecycle and final artifact.
VTO FRONT/DUAL add provider evidence, exact selected outfit, job retry/revoke/delete, returned-media validation and quality review per mode.
Collage/fake output never counts as live VTO evidence. A coordinates; B/C/D execute and sign their module/gate evidence.

No provider keys, tokens, private profile/body photos, personal identifiers or raw private DBs in public repo evidence.
Public licensed garment fixtures can be referenced by manifest ID; retain their attribution.
