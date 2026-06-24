# SDK upgrade safety

When changing the SDK, assume users upgrade from an older published version without clearing app data. Cached SharedPreferences, Gson models, and disk files must keep working.

## Checklist (before finishing a change)

1. **Persisted models** — Did you add/change fields on anything stored in SharedPreferences or disk (`ApphudUser`, properties, groups, etc.)?
   - New fields must be **nullable or have defaults**.
   - Never add a **non-nullable** field without a default to a cached Gson model.
   - Prefer **new preference keys** over reshaping existing JSON blobs.

2. **Network DTOs vs cache** — DTOs used only for live API parsing (`ResponseDto`, `DataDto`, `MetaDto`) are not upgrade risks unless their JSON is also persisted.

3. **Missing JSON fields** — Gson leaves absent keys as `null` for nullable Kotlin properties. Verify with a unit test or sample JSON **without** the new field.

4. **New preference keys** — Reading a missing key must return `null` and code must fall back safely (defaults, re-fetch from API).

5. **Cache version** — Large breaking cache shape changes may need `cacheVersion` migration in `SharedPreferencesStorage.validateCaches()` — not for additive optional fields.

6. **Public API** — Deprecated APIs should still work; don't remove or change behavior that existing apps rely on without a major version.

## Examples

### Safe: new optional API field + separate pref key

```kotlin
// DTO — network only, optional
internal data class DataDto<T>(
    val results: T?,
    val meta: MetaDto? = null,
)

// Storage — new key, null on first read after upgrade
override var connectDomainUrl: String?
    get() = preferences.getString(CONNECT_DOMAIN_URL_KEY, null)

// Reader — non-null with fallback
val connectDomainUrl: String
    get() = storage.connectDomainUrl ?: DEFAULT_CONNECT_DOMAIN_URL
```

### Unsafe: non-nullable field on cached model

```kotlin
// ❌ Old cached JSON has no `meta` → Gson/deserialization can fail or crash
data class ApphudUser(
    val userId: String,
    val meta: MetaDto,  // non-nullable, persisted in APPHUD_USER_KEY
)
```

## When to add tests

Add a test when you change persisted shapes or Gson DTOs:

- Deserialize **legacy JSON** (no new fields) — must not throw.
- Read storage with **empty / missing keys** — must use fallback.
- See `SharedPreferencesStorageMigrationTest` for cache migration patterns.
