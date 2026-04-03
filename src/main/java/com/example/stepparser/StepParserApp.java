package com.example.stepparser;

import com.example.stepparser.model.StepEntityInstance;
import com.example.stepparser.model.StepFile;
import com.example.stepparser.schema.StepSemanticModel;
import com.example.stepparser.schema.runtime.StepSchemaModel;

import java.io.IOException;
import java.nio.file.Path;

public final class StepParserApp {

    private StepParserApp() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && "--server".equals(args[0])) {
            runServer(args);
            return;
        }

        if (args.length != 1) {
            System.err.println(
                    "Usage: mvn compile exec:java -Dexec.args=\"/root/work/StepParser/examples/fan.stp\"");
            System.err.println(
                    "   or: mvn compile exec:java -Dexec.args=\"--server 8080\"");
            System.exit(1);
        }

        Path path = Path.of(args[0]);
        StepFile stepFile = StepFileParser.parse(path);
        StepSchemaModel schemaModel = StepFileParser.parseWithSchema(path);
        StepSemanticModel semanticModel = StepFileParser.parseSemantic(path);
        System.out.printf("Schemas: %s%n", stepFile.header().schemaNames());
        System.out.printf("Detected schema family: %s%n", semanticModel.schema());
        System.out.printf("Entities: %d%n", stepFile.data().entities().size());
        System.out.printf("Schema-mapped entities: %d%n", schemaModel.entities().size());
        System.out.printf("Products: %d%n", semanticModel.products().size());
        System.out.println(StepAssemblyTree.format(stepFile.data()));

        stepFile.data().entities().stream()
                .limit(5)
                .forEach(StepParserApp::printEntity);
    }

    private static void printEntity(StepEntityInstance entity) {
        System.out.printf("#%d = %s%n", entity.id(), entity.type());
    }

    private static void runServer(String[] args) throws IOException {
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        Path assetRoot = Path.of("target/http-assets");
        String commandTemplate = System.getenv("STEP_PARSER_GLB_EXPORT_COMMAND");
        StepGlbExporter exporter = commandTemplate == null || commandTemplate.isBlank()
                ? StepGlbExporter.disabled("GLB exporter is not configured. Set STEP_PARSER_GLB_EXPORT_COMMAND.")
                : new CommandLineGlbExporter(commandTemplate);

        StepParserHttpServer server = StepParserHttpServer.create(port, assetRoot, exporter);
        server.start();
        System.out.printf("STEP parser server listening on http://localhost:%d%n", port);
        System.out.println("GET /api/assembly-scene?stepFile=/absolute/path/to/file.step");
    }
}
