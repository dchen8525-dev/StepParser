# STEP Parser

Java 21 parser for ISO 10303-21 STEP physical files, suitable as a foundation for AP203, AP214, and AP242 readers.

## Current scope

- Parses `HEADER` and `DATA` sections
- Supports common STEP values:
  - strings
  - integers and reals
  - entity references like `#42`
  - enumerations like `.T.` and `.UNSPECIFIED.`
  - aggregates like `(1,#2,'x')`
  - omitted values `$`
  - derived values `*`
- Resolves cross references after parsing
- Detects schema names from `FILE_SCHEMA`
- Maps common entities into a typed semantic model
- Loads schema metadata for AP203/AP214/AP242
- Validates and maps schema-known entities dynamically
- Generates Java record types from loaded schema metadata
- Supports simple schema inheritance with `ENTITY X SUBTYPE_OF Y`

## Semantic API

The parser can also expose a typed semantic layer for common entities:

- detected schema family: AP203, AP214, AP242, or UNKNOWN
- `APPLICATION_CONTEXT`
- `PRODUCT`

Example:

```java
StepSemanticModel model = StepFileParser.parseSemantic(content);
System.out.println(model.schema());
System.out.println(model.products());
```

## Schema-driven API

For broader entity coverage, use the schema-driven layer:

```java
StepSchemaModel schemaModel = StepFileParser.parseWithSchema(content);
System.out.println(schemaModel.entities());
```

Generate Java record types for the detected schema:

```java
StepFileParser.generateSchemaTypes(content, Path.of("target/generated-sources"), "com.example.stepparser.generated");
```

## Run tests

```bash
mvn test
```

## Parse a STEP file

```bash
mvn exec:java -Dexec.args="sample.step"
```

When the file contains `NEXT_ASSEMBLY_USAGE_OCCURRENCE` relationships, the CLI also prints an assembly tree by resolving:

- `NEXT_ASSEMBLY_USAGE_OCCURRENCE`
- `PRODUCT_DEFINITION`
- `PRODUCT_DEFINITION_FORMATION`
- `PRODUCT`

## Next steps

- Add stricter validation for unresolved references
- Support additional Part 21 constructs if needed
- Expand built-in schema metadata files toward broader AP203/AP214/AP242 coverage
- Add EXPRESS select types and richer aggregate constraints
