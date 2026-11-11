# A-201 — garment sync mapping prototype

This is a local design/test boundary, not a cloud implementation or a user-facing backup feature.
`GarmentSyncMapping` exports/imports **metadata fixtures** without changing garment/media UUIDs, revisions, hashes, views or tombstones.
It includes no ProfileStorage, person photos, try-on results, credentials, provider URLs or remote calls. Input containing a PERSON ref is rejected.

Media identity is the opaque `garment/<media UUID>`, not a device path. Relative paths are reconstructed from scope, owner and media ID;
absolute paths are never accepted by the model. Byte transfer is a separate, future authenticated service, with hash/caps/ownership validation.
The prototype does not restore missing image bytes, import a live Room database, or claim cross-device backup works.

Draft conflict policy: tombstone wins regardless of clocks; an older export never resurrects a deleted ID. Equal active copies are idempotent;
differing copies of the same ID require explicit conflict resolution even if one claims a higher revision. Do not use wall-clock last-write-wins.
Revision lineage/server concurrency tokens need a separate contract before actual network sync.

Unit fixtures cover roundtrip, both directions of deletion merging, duplicate import, conflicting copies and default exclusion of person images.
D/C review remains required for profile/private-media ownership, retention, auth, deletion propagation and provider removal status.
No cloud account, billing, auth or sync engine has been provisioned.
