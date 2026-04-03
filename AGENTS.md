# Repository Guidelines

## Project Structure & Module Organization
`src/main/java/com/example/stepparser/` contains the parser, schema runtime, GLB export, and HTTP server code. Keep parsing logic under `parse/`, schema metadata and mapping under `schema/`, reference resolution under `resolve/`, and shared data types under `model/`. Tests live in `src/test/java/com/example/stepparser/`. Built-in STEP schema files are in `src/main/resources/schemas/`. Static viewer assets are in `frontend/`, and sample `.stp` inputs are in `examples/`.

## Build, Test, and Development Commands
Use Maven from the repository root:

- `mvn test` runs the JUnit 5 suite.
- `mvn compile` checks that Java 21 sources compile.
- `mvn exec:java -Dexec.args="sample.step"` runs the CLI parser.
- `mvn compile exec:java -Dexec.args="--server 8080"` starts the local HTTP server and serves `frontend/` at `http://localhost:8080/`.

## Coding Style & Naming Conventions
Follow the existing Java style: 4-space indentation, one top-level class or record per file, and `UpperCamelCase` type names such as `StepSchemaEngine`. Use `lowerCamelCase` for methods and fields, and keep package names lowercase. Prefer small immutable value types (`record` or final fields) where the codebase already uses them. Keep STEP entity names and schema constants uppercase when they mirror the standard. No formatter config is checked in, so match surrounding code exactly.

## Testing Guidelines
Tests use JUnit Jupiter via Maven Surefire. Add parser and schema coverage in `src/test/java/com/example/stepparser/StepFileParserTest.java` or a nearby `*Test.java` class. Name tests by behavior, for example `schemaRuntimeRejectsAbstractEntityInstantiation()`. Prefer inline text blocks for STEP fixtures and assert both happy-path parsing and failure cases with `assertThrows`.

## Commit & Pull Request Guidelines
Recent history uses very short commit messages like `update`. Keep commits concise, but prefer descriptive imperative subjects such as `parser: validate schema list bounds`. Pull requests should summarize behavior changes, list validation commands run, link the relevant issue, and include screenshots when `frontend/` behavior changes.

## Configuration & Assets
External GLB export is configured with `STEP_PARSER_GLB_EXPORT_COMMAND`, the `step.parser.glb-export-command` JVM property, or a local `.step-parser.properties` file. Do not commit machine-specific paths or generated files under `target/`.
