# Contributing

## Branches

- `main` contains the current stable state.
- Feature branches use `feature/<scope>-<name>`.
- Fix branches use `fix/<scope>-<name>`.
- Documentation branches use `docs/<name>`.

## Commits

Use concise conventional commit messages:

```text
feat(helpify): add task filtering
feat(mydealer): add vendor order actions
fix(api): validate expired access tokens
docs(security): update access-control requirements
test(android): add authentication tests
chore(ci): update quality checks
```

## Pull requests

A pull request must:

1. describe the change and affected product;
2. reference requirements or an issue where applicable;
3. include tests or verification steps;
4. pass repository checks;
5. contain no secrets, personal data or environment files.

## Releases

Release tags use semantic versioning:

```text
v0.8.1
v0.9.0
v1.0.0
```
