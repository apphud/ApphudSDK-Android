# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **ApphudSDK-Android** - an open-source SDK for managing auto-renewable subscriptions and in-app purchases on Android. The SDK handles subscription infrastructure, analytics, A/B experiments, and integrations with third-party platforms.

## Build and Test Commands

### Build
```bash
# Build debug variant
./gradlew :sdk:assembleDebug

# Build release variant
./gradlew :sdk:assembleRelease

# Build all variants
./gradlew :sdk:build
```

### Testing
```bash
# Run all unit tests
./gradlew :sdk:testDebugUnitTest

# Run specific test class
./gradlew :sdk:testDebugUnitTest --tests "ClassName"

# Run specific test method
./gradlew :sdk:testDebugUnitTest --tests "ClassName.methodName"

# Run tests without build cache
./gradlew :sdk:testDebugUnitTest --rerun-tasks
```

### Publishing
```bash
# Publish to local Maven (for testing)
./gradlew :sdk:publishToMavenLocal

# Publish to JitPack (default)
./gradlew :sdk:publish

# Publish to Maven Central
./gradlew :sdk:publish -PmavenCentral
```

## Architecture

### Core Architecture Pattern

The SDK uses a **layered architecture** with dependency injection via `ServiceLocator`:

1. **Public API Layer** (`Apphud.kt`) - Static facade exposing SDK functionality to clients
2. **Internal Implementation** (`ApphudInternal.kt` + extensions) - Core business logic split across multiple files using Kotlin extensions
3. **Domain Layer** (`internal/domain/`) - Use cases and business logic
4. **Data Layer** (`internal/data/`) - Repositories, data sources, network, and mappers
5. **Presentation Layer** (`internal/presentation/`) - UI components (Rules screens, paywalls)

### ServiceLocator Pattern

**All dependencies MUST be provided through `ServiceLocator`**. This is the single source of dependency creation and management.

Key components in ServiceLocator:
- `userRepository` / `userDataSource` - User state management
- `registrationUseCase` - User registration logic
- `remoteRepository` - Network API calls
- `ruleController` - Rules/screens functionality
- `paywallEventManager` - Paywall analytics

When adding new components:
1. Create the component in appropriate layer (domain/data)
2. Add it to `ServiceLocator` with proper dependencies
3. Access via `ServiceLocator.instance.componentName`

### ApphudInternal Structure

`ApphudInternal` is split across multiple files using Kotlin extensions:
- `ApphudInternal.kt` - Core initialization, registration, state management
- `ApphudInternal+Products.kt` - Product loading and management
- `ApphudInternal+Purchases.kt` - Purchase handling
- `ApphudInternal+RestorePurchases.kt` - Restore purchases flow
- `ApphudInternal+Attribution.kt` - Attribution tracking
- `ApphudInternal+Fallback.kt` - Fallback mode when network fails

### State Management

User state is managed through:
- `UserRepository` - Thread-safe access to current user (uses Mutex)
- `UserDataSource` - SharedPreferences persistence
- `ApphudUser` - Immutable domain model

**Important**: Never store `ApphudUser` instances in client code - always get fresh instance from repository.

### Registration Flow

Registration uses `RegistrationUseCase` which:
1. Checks cache (unless force registration)
2. Calls network API via `RequestManager`
3. Updates `UserRepository` with new user
4. Notifies listeners via `notifyLoadingCompleted()`

The flow is protected by a Mutex to prevent race conditions.

## Code Style and Patterns

### Coroutines Over Callbacks

**New code MUST use coroutines and suspend functions**. Legacy callback-based code exists but should not be extended.

Good:
```kotlin
suspend fun registration(): ApphudUser {
    return registrationUseCase(...)
}
```

Bad (legacy pattern, don't replicate):
```kotlin
fun registration(callback: (ApphudUser?, ApphudError?) -> Unit) {
    // Don't write new code like this
}
```

### Error Handling

**Always use `runCatchingCancellable` for suspend functions** instead of try-catch:

```kotlin
// Correct
runCatchingCancellable {
    doSomething()
}.onSuccess { result ->
    // handle success
}.onFailure { error ->
    // handle error
}

// Also correct
val result = runCatchingCancellable {
    doSomething()
}.getOrElse { error ->
    throw error.toApphudError()
}

// Wrong - don't use regular try-catch in suspend code
try {
    doSomething()
} catch (e: Exception) {
    // This doesn't properly handle CancellationException
}
```

For non-suspend code, regular `runCatching` is acceptable.

### Comments

**Do not write obvious comments**. Comments should explain "why", not "what".

Bad:
```kotlin
// Set user to repository
userRepository.setCurrentUser(user)
```

Good:
```kotlin
// Update cache to prevent redundant registration on next app launch
userRepository.setCurrentUser(user, saveToCache = true)
```

Or just no comment if the code is self-explanatory.

### Thread Safety

When dealing with mutable state:
- Use `Mutex` with `withLock` for suspend functions
- Use `@Volatile` for simple reads
- Use `synchronized` blocks for non-suspend code

Example from `UserRepository`:
```kotlin
private val mutex = Mutex()

@Volatile
private var currentUser: ApphudUser? = null

suspend fun setCurrentUser(user: ApphudUser) {
    mutex.withLock {
        currentUser = user
    }
}
```

## Package Structure

```
com.apphud.sdk/
├── Apphud.kt                    # Public API facade
├── ApphudInternal*.kt           # Core implementation (split via extensions)
├── domain/                      # Public domain models (ApphudUser, ApphudPaywall, etc.)
├── managers/                    # High-level managers (RequestManager, etc.)
└── internal/
    ├── ServiceLocator.kt        # Dependency injection container
    ├── domain/
    │   ├── UseCases             # Business logic (RegistrationUseCase, etc.)
    │   ├── mapper/              # Domain mappers
    │   └── model/               # Internal domain models
    ├── data/
    │   ├── UserRepository.kt    # State management
    │   ├── UserDataSource.kt    # Persistence
    │   ├── local/               # Local storage
    │   ├── remote/              # Network repositories
    │   ├── mapper/              # Data layer mappers
    │   └── network/             # HTTP interceptors
    ├── presentation/
    │   └── rule/                # UI for Rules feature
    └── util/
        └── Methods.kt           # runCatchingCancellable utilities
```

## Working with Legacy Code

The SDK has legacy callback-based code that is being migrated to coroutines:

1. **Don't extend legacy patterns** - write new code with suspend functions
2. **Mark legacy methods as @Deprecated** when creating suspend alternatives
3. **Keep backward compatibility** - deprecated methods should delegate to new implementations
4. **Migrate gradually** - old public APIs can stay for compatibility, internal code should migrate

Example migration pattern:
```kotlin
// New suspend implementation
private suspend fun registration(): ApphudUser {
    return performRegistration(forceRegistration = false)
}

// Deprecated legacy method for backward compatibility
@Deprecated("Use suspend registration() instead")
private fun registration(callback: (ApphudUser?, ApphudError?) -> Unit) {
    coroutineScope.launch {
        runCatchingCancellable {
            registration()
        }.onSuccess { user ->
            callback(user, null)
        }.onFailure { error ->
            callback(null, error.toApphudError())
        }
    }
}
```

## Agent Docs

Detailed guides are located in `agentdocs/`:
- [Testing Guide](agentdocs/unitTest.md) — how to write unit tests
