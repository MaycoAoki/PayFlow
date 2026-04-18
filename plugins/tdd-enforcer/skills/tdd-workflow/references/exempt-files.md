# Exempt Files — TDD Not Required

These file types and paths are automatically allowed by the TDD Enforcer hook without requiring a failing test first.

## Test Files (Always Allowed)

Writing test files IS the TDD workflow — they are always permitted.

| Pattern | Description |
|---------|-------------|
| `*.spec.*` | Spec files (Jest, Vitest, Jasmine, RSpec-style) |
| `*.test.*` | Test files (Jest, Vitest, Mocha) |
| `*_test.*` | Test files (Go, pytest) |
| `*Test.*` | Test files (JUnit, PHPUnit) |
| `*_spec.*` | Spec files (RSpec) |
| `*Spec.*` | Spec files (Spock) |
| `test_*.*` | Test files (pytest) |
| Files in `test/` | Test directory |
| Files in `tests/` | Test directory |
| Files in `__tests__/` | Jest convention |
| Files in `spec/` | RSpec convention |

## Configuration Files

| Pattern | Category |
|---------|----------|
| `*.config.*` | Framework configs (vite.config.ts, jest.config.js, etc.) |
| `.env*` | Environment variables |
| `*.json` | JSON configs (package.json, tsconfig.json, etc.) |
| `*.yaml` / `*.yml` | YAML configs |
| `*.toml` | TOML configs (pyproject.toml, Cargo.toml) |
| `*.ini` / `*.cfg` | INI configs |
| `*.lock` / `*.lockb` | Lock files (package-lock.json, bun.lockb) |
| `.editorconfig` | Editor config |
| `.nvmrc` / `.node-version` | Node version |
| `.tool-versions` | asdf version manager |

## Documentation

| Pattern | Category |
|---------|----------|
| `*.md` / `*.mdx` | Markdown |
| `*.txt` | Plain text |
| `*.rst` | reStructuredText |
| `*.adoc` | AsciiDoc |

## Styles / CSS

| Pattern | Category |
|---------|----------|
| `*.css` | CSS |
| `*.scss` | SASS |
| `*.less` | LESS |
| `*.sass` | SASS (indented) |
| `*.styl` | Stylus |

## Static Assets

| Pattern | Category |
|---------|----------|
| `*.svg` / `*.png` / `*.jpg` / `*.jpeg` | Images |
| `*.gif` / `*.ico` / `*.webp` / `*.avif` | Images |
| `*.woff` / `*.woff2` / `*.ttf` / `*.eot` / `*.otf` | Fonts |
| `*.mp4` / `*.webm` / `*.ogg` / `*.mp3` / `*.wav` | Media |

## Type Declarations

| Pattern | Category |
|---------|----------|
| `*.d.ts` | TypeScript declarations |
| `*.d.mts` | TypeScript ESM declarations |

## Database

| Pattern | Category |
|---------|----------|
| `*.prisma` | Prisma schema |
| `*.sql` | SQL files |
| Files in `migrations/` | Database migrations |
| Files in `seed/` / `seeds/` | Database seeds |
| Files in `prisma/` | Prisma directory |

## Build / CI / CD

| Pattern | Category |
|---------|----------|
| `Dockerfile*` / `docker-compose*` | Docker |
| `Makefile` / `Procfile` | Build files |
| `Jenkinsfile` | Jenkins |
| Files in `.github/` | GitHub Actions |
| `.gitlab-ci*` | GitLab CI |
| Files in `.circleci/` | CircleCI |

## Generated / Build Output

| Pattern | Category |
|---------|----------|
| Files in `generated/` | Generated code |
| Files in `dist/` | Build output |
| Files in `build/` | Build output |
| Files in `.next/` | Next.js build |
| Files in `node_modules/` | Dependencies |
| Files in `.turbo/` | Turborepo cache |
| Files in `coverage/` | Test coverage |

## Static / Public Directories

| Pattern | Category |
|---------|----------|
| Files in `public/` | Public assets |
| Files in `static/` | Static assets |

## Tooling / Scripts

| Pattern | Category |
|---------|----------|
| Files in `scripts/` | Build/deploy scripts |

## Linting / Formatting

| Pattern | Category |
|---------|----------|
| `.eslintrc*` / `.eslint.*` | ESLint |
| `.prettierrc*` / `.prettier*` | Prettier |
| `.stylelintrc*` | Stylelint |
| `.swcrc` | SWC |
| `.babelrc*` | Babel |

## Claude / Plugin Files

| Pattern | Category |
|---------|----------|
| `CLAUDE.md` | Claude instructions |
| `AGENTS.md` | Agent instructions |
| `hooks.json` | Hook config |
| `plugin.json` | Plugin manifest |
| `SKILL.md` | Skill definition |
| `.mcp.json` | MCP config |

## Git

| Pattern | Category |
|---------|----------|
| `.gitignore` | Git ignore rules |
| `.gitattributes` | Git attributes |
