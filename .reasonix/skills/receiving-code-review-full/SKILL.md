---
name: receiving-code-review-full
description: 原始版 Code Review 接收：读取→理解→验证→回应→实施，含禁止回复和反错误表
---

---
name: receiving-code-review
description: Use when receiving code review feedback, before implementing suggestions, especially if feedback seems unclear or technically questionable
---

# Code Review Reception

**Core principle:** Verify before implementing. Ask before assuming. Technical correctness over social comfort.

## The Response Pattern

1. **READ:** Complete feedback without reacting
2. **UNDERSTAND:** Restate requirement in own words (or ask)
3. **VERIFY:** Check against codebase reality
4. **EVALUATE:** Technically sound for THIS codebase?
5. **RESPOND:** Technical acknowledgment or reasoned pushback
6. **IMPLEMENT:** One item at a time, test each

## Forbidden Responses

**NEVER:**
- "You're absolutely right!" (explicit instruction-file violation)
- "Great point!" / "Excellent feedback!" (performative)
- "Let me implement that now" (before verification)

**INSTEAD:**
- Restate the technical requirement
- Ask clarifying questions
- Push back with technical reasoning if wrong
- Just start working (actions > words)

## Handling Unclear Feedback

STOP - do not implement anything yet. ASK for clarification on unclear items. Items may be related. Partial understanding = wrong implementation.

**Example:** "I understand items 1,2,3,6. Need clarification on 4 and 5 before proceeding."

## Source-Specific Handling

### From your human partner
- Trusted - implement after understanding
- Still ask if scope unclear
- No performative agreement
- Skip to action or technical acknowledgment

### From External Reviewers
BEFORE implementing:
1. Check: Technically correct for THIS codebase?
2. Check: Breaks existing functionality?
3. Check: Reason for current implementation?
4. Check: Works on all platforms/versions?
5. Check: Does reviewer understand full context?

IF suggestion seems wrong: Push back with technical reasoning.
IF can't easily verify: "I can't verify this without [X]. Should I [investigate/ask/proceed]?"

## YAGNI Check for "Professional" Features

IF reviewer suggests "implementing properly": grep codebase for actual usage. If unused: remove it (YAGNI)? If used: then implement properly.

## Implementation Order

1. Clarify anything unclear FIRST
2. Blocking issues (breaks, security) → Simple fixes → Complex fixes
3. Test each fix individually
4. Verify no regressions

## Acknowledging Correct Feedback

✅ "Fixed. [Brief description of what changed]"
✅ "Good catch - [specific issue]. Fixed in [location]."

❌ "You're absolutely right!"
❌ "Great point!"
❌ "Thanks for catching that!"
❌ ANY gratitude expression

**Why no thanks:** Actions speak. Just fix it.

## When To Push Back

- Suggestion breaks existing functionality
- Reviewer lacks full context
- Violates YAGNI (unused feature)
- Technically incorrect for this stack
- Legacy/compatibility reasons
- Conflicts with human partner's decisions

## GitHub Thread Replies

When replying to inline review comments on GitHub, reply in the comment thread (`gh api repos/{owner}/{repo}/pulls/{pr}/comments/{id}/replies`), not as a top-level PR comment.
