# Contributing

## Branches and Commits

- Use feature branches for non-trivial changes.
- Keep commits focused and describe the business or technical change clearly.
- Do not commit generated files, IDE metadata, local credentials or HTTP response caches.

## Code Style

- Keep controllers thin and place business orchestration in services.
- Prefer structured logging over console output.
- Keep configuration externalized through environment variables.
- Add tests for new business logic where practical.

## Pull Request Checklist

- [ ] `./mvnw test` passes.
- [ ] No secrets or local-only files are included.
- [ ] README or testing documentation is updated when behavior changes.
- [ ] Public APIs and DTOs are reviewed for backward compatibility.
