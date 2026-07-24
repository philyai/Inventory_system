# Repository Guidelines

## Project Structure & Module Organization

This repository contains a CommonJS Node.js REST API. `server.js` configures Express, connects Sequelize, and mounts feature routes. Keep HTTP route definitions in `routes/`, request handling and business logic in `controllers/`, Sequelize models and associations in `models/`, and reusable request concerns such as authentication, rate limiting, and uploads in `middleware/`. Project notes belong in `docs/`. Runtime item images are stored under `uploads/items/`; only `.gitkeep` should be committed there.

## Build, Test, and Development Commands

- `npm install` installs the locked dependencies from `package-lock.json`.
- `npm run dev` starts the API with Nodemon and reloads after source changes.
- `npm start` runs the production-style entry point with `node server.js`.
- `npm test` is currently a placeholder that exits with an error. Add and configure a test runner before relying on this command in CI.

There is no separate compilation step. The server defaults to `http://localhost:3001`.

## Coding Style & Naming Conventions

Follow the existing JavaScript style: two-space indentation, semicolons, single quotes, and `const`/`let` instead of `var`. Use camelCase for variables and functions (`generateItemCode`), PascalCase for Sequelize model constants (`ItemLocation`), and descriptive suffixes such as `*Controller.js` and `*Routes.js`. Database attributes follow the existing snake_case schema. Keep controllers focused, return explicit HTTP status codes, and pass authentication or upload concerns through middleware. No formatter or linter is configured, so review formatting manually and avoid unrelated reformatting.

## Testing Guidelines

Automated tests are not yet present. For new features, introduce tests in a top-level `tests/` directory using names such as `itemRoutes.test.js`. Cover successful requests, validation failures, authorization roles, and database errors. Until a test framework is added, manually exercise affected endpoints against a disposable database and document the commands or API client steps in the pull request.

## Commit & Pull Request Guidelines

The history currently contains only `Initial commit`, so no detailed convention is established. Use short, imperative commit subjects, for example `Add item image validation`. Keep commits scoped to one concern. Pull requests should explain the behavior change, list validation performed, link relevant issues, and call out schema or environment changes. Include sample requests/responses for API changes and screenshots only when they clarify uploaded-image or client-facing behavior.

## Security & Configuration

Create a local `.env` with `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_HOST`, `DB_PORT`, and optional `PORT`. Never commit `.env`, credentials, tokens, database dumps, or generated uploads. Preserve file-type and size validation when changing upload handling.
