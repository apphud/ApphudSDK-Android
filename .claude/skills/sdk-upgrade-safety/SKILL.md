---
name: sdk-upgrade-safety
description: >
  Ensures SDK changes do not crash or break apps upgrading from older SDK versions.
  Use when modifying SharedPreferences, Gson/cached models, DTOs, Storage, UserDataSource,
  cache migration, or any persisted SDK state. Use proactively before finishing SDK persistence
  or API model changes — not only when the user mentions upgrades.
---

Before finishing changes that touch persistence or serialized models, read: `agentdocs/sdkUpgrade.md`

## Required check

Assume users upgrade the SDK **without** clearing app data. Verify the change is safe for:

- Cached `ApphudUser` and other Gson objects in SharedPreferences
- New or changed preference keys
- Gson DTOs (missing JSON fields on older API responses)
- Disk/cache files and `cacheVersion` migrations

## Rules

1. **Never** add non-nullable fields without defaults to models that are **persisted**.
2. **Prefer** new SharedPreferences keys over changing existing JSON shapes.
3. **Nullable + default** for new DTO fields; network-only DTOs are lower risk than cached domain models.
4. **Fallback** when a pref key is missing after upgrade (`null` → default URL / empty / re-register).
5. **Test** legacy JSON / missing keys when the change is non-trivial.

## Workflow

1. Identify what is **persisted** vs **network-only**.
2. Walk the upgrade path: old SDK data → new SDK read path.
3. Add or extend tests if deserialization or storage reads changed.
4. Run `./gradlew :sdk:testDebugUnitTest` for affected tests.
