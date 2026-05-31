---
name: make-commit
description: >
  Creates atomic git commits for current changes — analyzes diff, groups by context and creates commits
  in "branch-name: Description" format. Use when the user says "make a commit", "commit changes",
  "create commit", "split into commits". NOT for review fixups (fixup + autosquash).
  NOT for rebuilding branch history (/recommit).
---

## Not for review fixups

- **Do not use `make-commit` for post-review fixes.**
- Post-review fixes should be done **only** via:
    - `git commit --fixup <hash>`
    - `git rebase -i --autosquash master`
- The branch should have a **final** set of commits without separate "post-review fixes".

## Commit Format

```
branch name: Brief description in English

More detailed description of changes in English.
```

## Instructions

1. **Analyze changes**: Review all modified and new files via `git status` and `git diff`
2. **Group by context**: Split changes into logical groups by context (e.g.: refactoring, new feature, bug fix, dependency updates)
3. **Create atomic commits**: Each commit should contain only related changes of one context
4. **Branch name**: Use current branch name from `git branch --show-current`
5. **Title**: Brief description (up to 72 characters) reflecting the essence of changes
6. **Commit body**: Detailed description:
    - What was changed
    - Why it was done (if not obvious)
    - Any side effects or important details

## Example

If current branch is `FlattenRegistration`, commit might look like:

```
FlattenRegistration: Fix email validation on login

Added email format validation before sending to server.
Fixed error handling for empty email field.
Updated unit tests to verify new validation.
```

## Post-commit checks

After creating all commits:

1. **Check recent commits**:
   ```bash
   git log -5 --format="%H%n%B%n---"
   ```

2. **Find commits with Co-Authored-By**:
    - Check each commit for lines starting with "Co-Authored-By:"
    - If found — clean them

3. **Remove Co-Authored-By from commits**:
   ```bash
   git commit --amend -m "$(git log -1 --format=%B | sed '/^Co-Authored-By:/,$d')"
   ```

## Rules

- One commit = one context of changes
- Don't mix refactoring with new features
- Don't include unrelated files in one commit
- Description in English
- Follow Git Convention: title should complete the phrase "This commit..."
