# recommit

Command to restructure all commits in the current branch. Uncommits all changes in the branch to local changes and then creates atomic commits following the make_commit rules.

## Commit Format

```
branch name: Brief description in English

More detailed description of changes in English.
```

## Instructions

1. **Find branch divergence point**:
   - Get current branch name via `git branch --show-current`
   - Find commits in current branch not in master: `git log --oneline --first-parent master..HEAD`
   - Number of such commits will be used to determine base point: `HEAD~<count>`
   - This will be the base point for uncommit
2. **CRITICAL - Verify commits before uncommit**:
   - Get list of commits via `git log --oneline --first-parent master..HEAD`
   - **SHOW user complete list of commits to be removed** in format:
     ```
     The following commits will be removed:
     <hash1> <message1>
     <hash2> <message2>
     ...

     Total: <N> commits
     ```
   - **REQUEST user confirmation** with clear question:
     ```
     Do you really want to remove these <N> commits and rebuild them? (yes/no)
     ```
   - Continue only if user entered "yes" (case-insensitive)
   - **IF user declined - STOP** and report operation cancelled
3. **Preserve changes**: Execute `git reset --soft HEAD~<count>` to uncommit all commits in branch while keeping all changes staged
4. **Unstage changes**: Execute `git reset HEAD` to move all changes to unstaged
5. **Analyze changes**: Review all modified and new files via `git status` and `git diff`
6. **Group by context**: Split changes into logical groups by context (e.g.:
   refactoring, new feature, bug fix, dependency updates)
7. **Create atomic commits**: Each commit should contain only related changes of one context
8. **Branch name**: Use current branch name from `git branch --show-current`
9. **Title**: Brief description (up to 72 characters) reflecting the essence of changes
10. **Commit body**: Detailed description:
   - What was changed
   - Why it was done (if not obvious)
   - Any side effects or important details

## Example

If current branch `FlattenRegistration` had 5 messy commits, the command will:
1. Uncommit all 5 commits to local changes
2. Analyze all changes
3. Create new atomic commits, for example:

```
FlattenRegistration: Add UserRepository for state management

Implemented thread-safe user state management using Mutex.
Added UserDataSource for SharedPreferences operations.
```

```
FlattenRegistration: Refactor registration to use coroutines

Converted callback-based registration to suspend functions.
Created RegistrationUseCase to handle business logic.
```

```
FlattenRegistration: Update dependencies for coroutines

Updated kotlinx-coroutines-core to version 1.7.3.
Updated kotlinx-coroutines-android to version 1.7.3.
```

## Important

- **Warning**: This command rewrites commit history!
- One commit = one context of changes
- Don't mix refactoring with new features
- Don't include unrelated files in one commit
- Description in English
- Follow Git Convention: title should complete the phrase "This commit..."
- Command uses `git log --first-parent master..HEAD` to determine commits in current branch not in master