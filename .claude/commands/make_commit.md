     # make_commit

     Command to create atomic git commits based on current changes.

     ## Commit Format

     ```
     branch name: Brief description in English

     More detailed description of changes in English.
     ```

     ## Instructions

     1. **Analyze changes**: Review all modified and new files via `git status` and `git diff`
     2. **Group by context**: Split changes into logical groups by context (e.g.:
     refactoring, new feature, bug fix, dependency updates)
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

     ## Important

     - One commit = one context of changes
     - Don't mix refactoring with new features
     - Don't include unrelated files in one commit
     - Description in English
     - Follow Git Convention: title should complete the phrase "This commit..."