# Issue tracker: br (beads_rust)

Issues for this repo live in br (beads_rust), an agent-first SQLite + JSONL issue tracker.
The workspace is at `.beads/` with issue prefix `crap`.

## Conventions

- **Create an issue**: `br create "Title" --type task --priority P2 --description "..."`
- **Read an issue**: `br show <id>` (supports `--format json` or `--format toon`)
- **List issues**: `br list --status open` with `--type`, `--label`, `--priority`, `--assignee` filters
- **Search issues**: `br search "query" --status open`
- **Find ready work**: `br ready` (open, unblocked, not deferred)
- **Update an issue**: `br update <id> --status in_progress`
- **Comment on an issue**: `br comments add <id> "..."`
- **Apply labels**: `br label add <id> "label-name"`
- **Remove labels**: `br label remove <id> "label-name"`
- **Close**: `br close <id>`
- **Dependencies**: `br dep add <id> --blocks <other-id>`

## Syncing

`br` never runs git commands. After any changes, sync and commit manually:

```bash
br sync --flush-only
git add .beads/
git commit -m "sync beads"
```

## When a skill says "publish to the issue tracker"

Create a br issue with `br create`.

## When a skill says "fetch the relevant ticket"

Run `br show <id>`.
