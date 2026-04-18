# Test Patterns by Framework

## Framework Detection

To identify the project's test framework, check these files in order:

| Check | Framework | Test Command |
|-------|-----------|-------------|
| `jest.config.*` or `jest` in `package.json` | Jest | `npx jest` |
| `vitest.config.*` or `vitest` in `package.json` | Vitest | `npx vitest run` |
| `bun.lockb` + test scripts | Bun Test | `bun test` |
| `pytest.ini`, `pyproject.toml` `[tool.pytest]` | pytest | `pytest` |
| `go.mod` | Go testing | `go test ./...` |
| `Cargo.toml` | Rust cargo test | `cargo test` |
| `build.gradle` or `pom.xml` | JUnit | `gradle test` / `mvn test` |
| `Gemfile` with `rspec` | RSpec | `bundle exec rspec` |
| `mix.exs` | ExUnit | `mix test` |
| `Package.swift` | XCTest | `swift test` |
| `composer.json` with `phpunit` | PHPUnit | `vendor/bin/phpunit` |

## File Naming Conventions

### JavaScript / TypeScript

| Framework | Pattern | Example |
|-----------|---------|---------|
| Jest | `*.spec.ts`, `*.test.ts` | `auth.service.spec.ts` |
| Vitest | `*.spec.ts`, `*.test.ts` | `useAuth.test.ts` |
| Mocha | `*.spec.js`, `*.test.js` | `api.spec.js` |

**Test directories:** `test/`, `tests/`, `__tests__/`, or colocated with source.

### Python

| Framework | Pattern | Example |
|-----------|---------|---------|
| pytest | `test_*.py`, `*_test.py` | `test_auth.py` |
| unittest | `test_*.py` | `test_models.py` |

**Test directory:** `tests/`

### Go

| Pattern | Example |
|---------|---------|
| `*_test.go` (same package) | `auth_test.go` |

**Colocated with source files.**

### Java / Kotlin

| Framework | Pattern | Example |
|-----------|---------|---------|
| JUnit | `*Test.java` | `AuthServiceTest.java` |
| Spock | `*Spec.groovy` | `AuthServiceSpec.groovy` |

**Test directory:** `src/test/java/`

### Ruby

| Framework | Pattern | Example |
|-----------|---------|---------|
| RSpec | `*_spec.rb` | `auth_service_spec.rb` |
| Minitest | `*_test.rb` | `auth_service_test.rb` |

**Test directory:** `spec/` (RSpec), `test/` (Minitest)

### Rust

| Pattern | Example |
|---------|---------|
| Inline `#[cfg(test)] mod tests` | In source file |
| `tests/*.rs` (integration) | `tests/auth_test.rs` |

### Elixir

| Pattern | Example |
|---------|---------|
| `*_test.exs` | `auth_test.exs` |

**Test directory:** `test/`

### PHP

| Framework | Pattern | Example |
|-----------|---------|---------|
| PHPUnit | `*Test.php` | `AuthServiceTest.php` |

**Test directory:** `tests/`

## Running Single Tests

### Jest
```bash
npx jest path/to/file.spec.ts
npx jest --testPathPattern=auth.service
npx jest -t "should validate email"
```

### Vitest
```bash
npx vitest run path/to/file.test.ts
bun test path/to/file.test.ts
```

### pytest
```bash
pytest tests/test_auth.py
pytest tests/test_auth.py::test_login
pytest -k "test_login"
```

### Go
```bash
go test ./pkg/auth
go test -run TestLogin ./pkg/auth
```

### cargo
```bash
cargo test test_login
cargo test --test integration_test
```

### RSpec
```bash
bundle exec rspec spec/models/user_spec.rb
bundle exec rspec spec/models/user_spec.rb:42
```

### JUnit (Gradle)
```bash
gradle test --tests AuthServiceTest
gradle test --tests "*.AuthServiceTest.testLogin"
```

## Custom Test Patterns

For projects with non-standard test commands, create a `.tdd-test-patterns` file in the project root:

```
# .tdd-test-patterns
# One pattern per line. Lines starting with # are comments.
# Patterns are matched as substrings against the executed command.

./run-tests.sh
make test
make check
custom-runner --test
```

The following common wrappers are also detected automatically (no `.tdd-test-patterns` needed):
- `./run-tests.sh`, `./run_tests.sh`, `./test.sh`
- `make test`, `make check`
- `swift test`

## Watch Mode

| Framework | Command |
|-----------|---------|
| Jest | `npx jest --watch` |
| Vitest | `npx vitest` (default) |
| pytest | `ptw` or `pytest-watch` |
| Go | `gotestsum --watch` |
| cargo | `cargo watch -x test` |
| RSpec | `bundle exec guard` |
