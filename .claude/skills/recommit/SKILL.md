---
name: recommit
description: >
  Fully restructures branch commit history — resets all commits to master, regroups changes and creates
  clean atomic history. Use when the user says "rebuild commit history", "regroup commits",
  "history is messy — clean it up", "recommit", "split commits properly".
  DANGEROUS operation — rewrites history, requires confirmation.
  NOT for single fixes (fixup). NOT for just adding a commit (/make-commit).
---

## Purpose and constraints

- `recommit` is used for **rebuilding branch history** when it became "dirty" (too many non-atomic commits).
- Base branch is always **master**.
- Result should follow project policy:
    - **final** history (each commit reflects the final code),
    - **no** separate "post-review fixes" commits.
- Post-review fixes should be done via **fixup+autosquash**:
    - `git commit --fixup <hash>` -> `git rebase -i --autosquash master`

Use `recommit` only if fixup/autosquash can't help or you need to fully rebuild the history.

## Commit Format

```
branch name: Brief description in English

More detailed description of changes in English.
```

## Instructions

1. **Find branch divergence point**:
    - Get current branch name: `git branch --show-current`
    - Find commits in current branch not in master: `git log --oneline --first-parent master..HEAD`
    - Number of such commits = base point: `HEAD~<count>`

2. **CRITICAL - Verify commits before uncommit**:
    - Show user the complete list of commits to be removed
    - Request confirmation: "Do you really want to remove these N commits and rebuild them? (yes/no)"
    - Continue only if user entered "yes"
    - If declined — STOP

3. **Preserve changes**: Execute `git reset --soft HEAD~<count>`
4. **Unstage changes**: Execute `git reset HEAD`
5. **Analyze changes**: Review all files via `git status` and `git diff`
6. **Group by context**: Split into logical groups
7. **Create atomic commits**: Each commit = one context

## Important

- **Warning**: This command rewrites commit history!
- **Commit messages must be based strictly on diffs**: Do NOT copy old commit messages — generate new descriptions based solely on the code changes
- One commit = one context of changes
- Don't mix refactoring with new features
- Description in English
- Uses `git log --first-parent master..HEAD` to determine branch commits
