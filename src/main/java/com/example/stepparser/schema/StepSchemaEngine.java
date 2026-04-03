package com.example.stepparser.schema;

import com.example.stepparser.model.StepFile;
import com.example.stepparser.model.StepSchemas;
import com.example.stepparser.schema.generate.StepJavaCodeGenerator;
import com.example.stepparser.schema.meta.StepSchemaDefinition;
import com.example.stepparser.schema.meta.StepSchemaLoader;
import com.example.stepparser.schema.runtime.StepSchemaModel;
import com.example.stepparser.schema.runtime.StepSchemaRuntimeMapper;

import java.nio.file.Path;
import java.util.List;

public final class StepSchemaEngine {

    private StepSchemaEngine() {
    }

    public static StepSchemaDefinition loadDefinition(StepFile file) {
        StepSchemas family = StepSchemas.detect(file.header().schemaNames());
        return switch (family) {
            case AP203 -> StepSchemaLoader.loadBuiltin("AP203");
            case AP214 -> StepSchemaLoader.loadBuiltin("AP214");
            case AP242 -> StepSchemaLoader.loadBuiltin("AP242");
            case UNKNOWN -> throw new StepSemanticException("Cannot choose built-in schema metadata from FILE_SCHEMA " + file.header().schemaNames());
        };
    }

    public static StepSchemaModel map(StepFile file) {
        StepSchemaDefinition definition = loadDefinition(file);
        return StepSchemaRuntimeMapper.map(file, definition);
    }

    public static List<Path> generateJavaTypes(StepFile file, Path outputRoot, String basePackage) {
        StepSchemaDefinition definition = loadDefinition(file);
        return StepJavaCodeGenerator.generate(definition, outputRoot, basePackage);
    }
}
