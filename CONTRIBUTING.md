# Contributing

This project is developed by a single author, but it follows team conventions deliberately. The point is that the history should be readable by someone who was not there when it was written.

## Workflow

Every change follows the same path:

1. **Open an issue.** Describe the outcome, not the implementation. Assign it to a milestone.
2. **Branch from `main`.** Naming: `feat/<short-slug>`, `fix/<short-slug>`, `chore/<short-slug>`, `docs/<short-slug>`.
3. **Commit in small steps.** A commit should leave the build in a working state and do one thing.
4. **Open a pull request.** Reference the issue with `Closes #12`. Review your own diff before merging — comment on anything you would question in someone else's code.
5. **Update the changelog** in the same pull request as the change it describes.
6. **Merge to `main`** once CI is green. `main` is always deployable.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/), which keeps the history greppable and makes release notes mechanical.

```
<type>(<scope>): <imperative summary>

<body: why, not what>

Refs #12
```

Types in use: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`, `perf`, `revert`.

Examples:

```
feat(api): accept Idempotency-Key header on transaction intake
fix(outbox): release the advisory lock when publication fails
test(notifier): assert duplicate delivery leaves balance unchanged
ci(actions): cache the Maven repository between runs
```

The body matters more than the summary. Six months from now the question is never *what* changed — the diff shows that — it is *why* that approach was chosen.

## Traceability

Three links are never skipped, because together they let anyone reconstruct the reasoning behind any line of code:

- **Commit → issue.** Every commit body ends with `Refs #N`.
- **Pull request → issue.** Every PR body contains `Closes #N`.
- **Decision → ADR.** Any pull request that changes the architecture links the ADR that justifies it, and any ADR that is superseded links the one replacing it.

## Architecture decision records

When a decision is hard to reverse, affects more than one module, or is one that a reviewer would ask about, write an ADR before writing the code. Copy `docs/adr/0000-template.md`, number it sequentially, and never edit a decision once accepted — supersede it with a new record instead.

## Definition of done

A change is done when the code works, the tests cover the failure case and not just the happy path, the public behaviour is documented, the changelog is updated, and CI is green.
