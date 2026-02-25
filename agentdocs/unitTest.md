# Unit Testing Guide

## Stack

- **JUnit 4** — test runner
- **MockK 1.13.8** — mocking library
- **kotlinx-coroutines-test 1.7.3** — `runTest` for suspend functions

## File Structure

Test files mirror `src/main/` layout:

```
sdk/src/test/java/com/apphud/sdk/
└── internal/
    └── domain/
        └── ResolveCredentialsUseCaseTest.kt   # tests for ResolveCredentialsUseCase
```

Class under test `com.apphud.sdk.internal.domain.Foo` → test at `sdk/src/test/java/com/apphud/sdk/internal/domain/FooTest.kt`.

## Test Naming

Use backtick-quoted names in `GIVEN <condition> EXPECT <result>` format:

```kotlin
@Test
fun `GIVEN input userId EXPECT saves input userId`() { ... }

@Test
fun `GIVEN no input and no cache EXPECT generates non-blank userId`() { ... }

@Test
fun `GIVEN input matches cache EXPECT credentialsChanged is false`() { ... }
```

Keep names concise. GIVEN describes the setup, EXPECT describes the single assertion.

## One Test = One Assertion

Each test verifies exactly one thing. If you need to check multiple outcomes of the same action, write separate tests:

```kotlin
// Good — separate tests for each verification
@Test
fun `GIVEN input userId EXPECT saves input userId`() {
    every { userRepository.getUserId() } returns "cached-user"
    every { userRepository.getDeviceId() } returns "cached-device"

    useCase(inputUserId = "input-user", inputDeviceId = "input-device")

    verify { userRepository.setUserId("input-user") }
}

@Test
fun `GIVEN input deviceId EXPECT saves input deviceId`() {
    every { userRepository.getUserId() } returns "cached-user"
    every { userRepository.getDeviceId() } returns "cached-device"

    useCase(inputUserId = "input-user", inputDeviceId = "input-device")

    verify { userRepository.setDeviceId("input-device") }
}

// Bad — multiple verifications in one test
@Test
fun `GIVEN input EXPECT saves both userId and deviceId`() {
    // ...
    verify { userRepository.setUserId("input-user") }
    verify { userRepository.setDeviceId("input-device") }  // second assertion
}
```

## Class Structure

Declare mocks and the subject under test (SUT) as `val` fields. Do NOT use `lateinit var` or `@Before` setup:

```kotlin
class ResolveCredentialsUseCaseTest {

    private val userRepository: UserRepository = mockk {
        every { setUserId(any()) } returns Unit
        every { setDeviceId(any()) } returns Unit
    }
    private val useCase = ResolveCredentialsUseCase(userRepository)

    @Test
    fun `GIVEN ...`() { ... }
}
```

Default stubs shared by all tests go into the `mockk { }` initializer block. Per-test stubs go inside the test method.

## MockK Rules

### No `relaxed = true`

Always stub explicitly. `relaxed = true` hides missing stubs and can mask bugs:

```kotlin
// Good
private val userRepository: UserRepository = mockk {
    every { setUserId(any()) } returns Unit
    every { setDeviceId(any()) } returns Unit
}

// Bad
private val userRepository: UserRepository = mockk(relaxed = true)
```

### `every`/`verify` vs `coEvery`/`coVerify`

Use `every`/`verify` for regular functions, `coEvery`/`coVerify` for suspend functions:

```kotlin
// Regular function
every { repository.getUserId() } returns "user-id"
verify { repository.setUserId("user-id") }

// Suspend function
coEvery { repository.fetchUser() } returns user
coVerify { repository.fetchUser() }
```

### `capture()` for Argument Capture

Use `capture()` when you need to inspect the actual value passed to a function:

```kotlin
@Test
fun `GIVEN no input and no cache EXPECT userId and deviceId share the same UUID`() {
    every { userRepository.getUserId() } returns null
    every { userRepository.getDeviceId() } returns null

    val capturedUserId = mutableListOf<String>()
    val capturedDeviceId = mutableListOf<String>()
    every { userRepository.setUserId(capture(capturedUserId)) } returns Unit
    every { userRepository.setDeviceId(capture(capturedDeviceId)) } returns Unit

    useCase(inputUserId = null, inputDeviceId = null)

    assertEquals(capturedUserId.single(), capturedDeviceId.single())
}
```

### `match { }` for Flexible Matchers

Use `match { }` when you need custom matching logic:

```kotlin
verify { userRepository.setUserId(match { it.isNotBlank() }) }
```

## Suspend Functions

Wrap suspend test bodies in `runTest`:

```kotlin
@Test
fun `GIVEN valid input EXPECT returns user`() = runTest {
    coEvery { repository.fetchUser() } returns expectedUser

    val result = useCase()

    assertEquals(expectedUser, result)
}
```

## Test Organization

Group related tests with `// region` / `// endregion` comments:

```kotlin
// region input provided

@Test
fun `GIVEN input userId EXPECT saves input userId`() { ... }

@Test
fun `GIVEN input deviceId EXPECT saves input deviceId`() { ... }

@Test
fun `GIVEN input differs from cache EXPECT credentialsChanged is true`() { ... }

// endregion

// region input null -- fallback to cache

@Test
fun `GIVEN null input and cache exists EXPECT saves cached userId`() { ... }

// endregion
```

Region names describe the scenario being tested.

## Running Tests

```bash
# Run all unit tests
./gradlew :sdk:testDebugUnitTest

# Run specific test class
./gradlew :sdk:testDebugUnitTest --tests "ResolveCredentialsUseCaseTest"

# Run specific test method
./gradlew :sdk:testDebugUnitTest --tests "ResolveCredentialsUseCaseTest.GIVEN input userId EXPECT saves input userId"

# Run tests without build cache
./gradlew :sdk:testDebugUnitTest --rerun-tasks
```
