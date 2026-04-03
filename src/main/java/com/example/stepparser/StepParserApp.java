package com.example.stepparser;

import com.example.stepparser.model.StepEntityInstance;
import com.example.stepparser.model.StepFile;
import com.example.stepparser.schema.StepSemanticModel;
import com.example.stepparser.schema.runtime.StepSchemaModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StepParserApp {

    private StepParserApp() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: mvn exec:java -Dexec.args=\"<path-to.step>\"");
            System.exit(1);
        }

        Path path = Path.of(args[0]);
        String content = Files.readString(path);

        StepFile stepFile = StepFileParser.parse(content);
        StepSchemaModel schemaModel = StepFileParser.parseWithSchema(content);
        StepSemanticModel semanticModel = StepFileParser.parseSemantic(content);
        System.out.printf("Schemas: %s%n", stepFile.header().schemaNames());
        System.out.printf("Detected schema family: %s%n", semanticModel.schema());
        System.out.printf("Entities: %d%n", stepFile.data().entities().size());
        System.out.printf("Schema-mapped entities: %d%n", schemaModel.entities().size());
        System.out.printf("Products: %d%n", semanticModel.products().size());

        stepFile.data().entities().stream()
                .limit(5)
                .forEach(StepParserApp::printEntity);
    }

    private static void printEntity(StepEntityInstance entity) {
        System.out.printf("#%d = %s%n", entity.id(), entity.type());
    }
}
