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

## Frontend scene API

The project now includes:

- a small HTTP API that returns assembly-tree data together with per-definition GLB asset references
- a Babylon.js frontend served from `/`

Start the server:

```bash
mvn compile exec:java -Dexec.args="--server 8080"
```

Open the viewer:

```text
http://localhost:8080/
```

Load a STEP file:

```bash
curl "http://localhost:8080/api/assembly-scene?stepFile=/absolute/path/to/model.step"
```

The response shape is:

```json
{
  "sourceStepFile": "/absolute/path/to/model.step",
  "schemaNames": ["AUTOMOTIVE_DESIGN"],
  "roots": [
    {
      "instanceId": "def-40",
      "definitionId": 40,
      "displayName": "Root Assembly",
      "glb": {
        "fileName": "Root-Assembly-40.glb",
        "relativeUri": "/assets/<request-id>/Root-Assembly-40.glb",
        "exported": true,
        "error": null
      },
      "children": []
    }
  ],
  "warnings": []
}
```

Generated GLB files are served by the same server under `/assets/...`.

## Babylon.js frontend

The frontend is a static app in `frontend/` and is served directly by the Java HTTP server.

Features:

- STEP file path input
- assembly-tree sidebar
- Babylon.js 3D viewport
- click a tree node to highlight and isolate that part
- fit-to-view camera reset button

The frontend expects each node-level GLB to already contain usable geometry placement for the whole assembly scene.

## GLB exporter integration

This repo now includes a built-in Java GLB exporter. It writes valid `.glb` files without any external tool, approximating `MANIFOLD_SOLID_BREP` geometry from face boundary loops and falling back to placeholder preview meshes when that is not possible.

If you want real STEP-derived geometry instead of placeholders, you can still delegate `.glb` generation to an external command.

The server resolves the external exporter command in this order:

- `STEP_PARSER_GLB_EXPORT_COMMAND` environment variable
- `step.parser.glb-export-command` JVM system property
- `step.parser.glb-export-command` entry in a local `.step-parser.properties` file

Example:

```bash
export STEP_PARSER_GLB_EXPORT_COMMAND="your-step-to-glb-tool --input {stepFile} --definition {definitionId} --output {outputFile}"
```

You can also start the server with a JVM property:

```bash
mvn compile exec:java -Dexec.args="--server 8080" -Dstep.parser.glb-export-command="your-step-to-glb-tool --input {stepFile} --definition {definitionId} --output {outputFile}"
```

Or create `.step-parser.properties` in the project root:

```properties
step.parser.glb-export-command=your-step-to-glb-tool --input {stepFile} --definition {definitionId} --output {outputFile}
```

Available placeholders:

- `{stepFile}`
- `{outputFile}`
- `{definitionId}`
- `{formationEntityId}`
- `{productEntityId}`
- `{productId}`
- `{productName}`

If no external exporter command is configured, the server falls back to the built-in Java exporter and tries to approximate `MANIFOLD_SOLID_BREP` shapes directly in Java.

The Babylon.js viewer will still load either way. With the built-in exporter, curved surfaces are still crude and unsupported definitions still fall back to placeholders; with an external exporter, you can provide real tessellated meshes.

## Next steps

- Add stricter validation for unresolved references
- Support additional Part 21 constructs if needed
- Expand built-in schema metadata files toward broader AP203/AP214/AP242 coverage
- Add EXPRESS select types and richer aggregate constraints
