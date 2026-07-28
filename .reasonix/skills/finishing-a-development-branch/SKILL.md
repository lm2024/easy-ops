---
name: finishing-a-development-branch
description: Git 开发分支收尾：验证测试→检测环境→展示选项→执行→清理，含完整 git 命令和反错误表
---

---
name: finishing-a-development-branch
description: Use when implementation is complete, all tests pass, and you need to decide how to integrate the work
---

# Finishing a Development Branch

## Overview

**Core principle:** Verify tests → Detect environment → Present options → Execute choice → Clean up.

**Announce at start:** "I'm using the finishing-a-development-branch skill to complete this work."

## Step 1: Verify Tests

Run the project's full test suite (`npm test` / `cargo test` / `pytest` / `go test ./...`).

**If tests fail**, report the failures and stop — the menu comes after a green suite:

```
Tests failing (<N> failures). Must fix before completing:

[Show failures]
```

**If tests pass:** continue to Step 2.

## Step 2: Detect Environment

```bash
GIT_DIR=$(cd "$(git rev-parse --git-dir)" 2>/dev/null && pwd -P)
GIT_COMMON=$(cd "$(git rev-parse --git-common-dir)" 2>/dev/null && pwd -P)
WORKTREE_PATH=$(git rev-parse --show-toplevel)
```

| State | Menu | Cleanup |
|-------|------|-----------|
| `GIT_DIR == GIT_COMMON` (normal repo) | Standard 3 options | No worktree to clean up |
| `GIT_DIR != GIT_COMMON`, named branch | Standard 3 options | Provenance-based |
| `GIT_DIR != GIT_COMMON`, detached HEAD | Reduced 2 options (no merge) | Externally managed — leave in place |

## Step 3: Determine Base Branch

The base branch is whatever this work forked from. Confirm before merging: merging into the wrong base is expensive to undo.

## Step 4: Present Options

**Normal repo / named-branch worktree:**
1. Merge back to <base-branch> locally
2. Push and create a Pull Request
3. Keep the branch as-is (handle it later)

**Detached HEAD:**
1. Push as new branch and create a PR
2. Keep as-is

Present the menu exactly as written. Wait for the user's answer.

## Step 5: Execute Choice

### Option 1: Merge Locally
```bash
git checkout <base-branch> && git pull && git merge <feature-branch>
<test command>  # verify on merged result
git branch -d <feature-branch>
```

### Option 2: Push and Create PR
```bash
git push -u origin <feature-branch>
# Then create the PR with the forge's tooling
```

### Option 3: Keep As-Is
Report: "Keeping branch <name>. Worktree preserved at <path>."

## Step 6: Cleanup Workspace

**Runs for Option 1 and confirmed discards. Options 2 and 3 preserve the worktree.**

**If under `.worktrees/` or `worktrees/`:**
```bash
git worktree remove "$WORKTREE_PATH"
git worktree prune
```

**Otherwise:** Leave in place — host environment owns it.

## Rationalizations to Avoid

| Excuse | Reality |
|--------|---------|
| "Tests passed earlier this session" | Run the suite on the tree you are about to integrate |
| "They obviously want it merged" | Integration is your human partner's decision |
| "'Yeah, get rid of it' counts as confirmation" | Only the typed word `discard` authorizes deletion |
| "The push was rejected — force-push will fix it" | Investigate; force-push only on explicit request |
