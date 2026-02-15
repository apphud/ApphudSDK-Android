Before writing or modifying unit tests, read the testing guide:

$ARGUMENTS

## Checklist

Follow these rules when writing tests:

1. **Read the guide first** — `agentdocs/unitTest.md`
2. **Naming** — use `` `GIVEN <condition> EXPECT <result>` `` format
3. **One test = one assertion** — split multiple verifications into separate tests
4. **`val` fields** — declare mocks and SUT as `val`, no `lateinit var` or `@Before`
5. **No `relaxed = true`** — always stub explicitly
6. **`every`/`coEvery`** — use `co*` variants for suspend functions only
7. **`runTest`** — wrap suspend test bodies in `runTest { }`
8. **Region grouping** — use `// region` / `// endregion` to group related tests
9. **Run tests after writing** — verify everything passes

## Commands

```bash
# Run all tests
./gradlew :sdk:testDebugUnitTest

# Run specific class
./gradlew :sdk:testDebugUnitTest --tests "ClassName"

# Run specific method
./gradlew :sdk:testDebugUnitTest --tests "ClassName.methodName"
```

## Workflow

1. Read `agentdocs/unitTest.md` for patterns and examples
2. Read the class under test to understand its behavior
3. Write tests following the checklist above
4. Run the tests to verify they pass
