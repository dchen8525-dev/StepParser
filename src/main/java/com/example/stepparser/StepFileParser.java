package com.example.stepparser;

import com.example.stepparser.parse.StepParser;
import com.example.stepparser.resolve.StepReferenceResolver;
import com.example.stepparser.model.StepFile;
import com.example.stepparser.schema.StepSemanticMapper;
import com.example.stepparser.schema.StepSemanticModel;
import com.example.stepparser.schema.StepSchemaEngine;
import com.example.stepparser.schema.runtime.StepSchemaModel;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class StepFileParser {

    private StepFileParser() {
    }

    public static StepFile parse(Path path) throws IOException {
        return parse(readContent(path));
    }

    public static StepFile parse(String content) {
        StepParser parser = new StepParser(content);
        StepFile file = parser.parse();
        return StepReferenceResolver.resolve(file);
    }

    public static StepSemanticModel parseSemantic(Path path) throws IOException {
        return parseSemantic(readContent(path));
    }

    public static StepSemanticModel parseSemantic(String content) {
        return StepSemanticMapper.map(parse(content));
    }

    public static StepSchemaModel parseWithSchema(Path path) throws IOException {
        return parseWithSchema(readContent(path));
    }

    public static StepSchemaModel parseWithSchema(String content) {
        return StepSchemaEngine.map(parse(content));
    }

    public static List<Path> generateSchemaTypes(Path path, Path outputRoot, String basePackage) throws IOException {
        return generateSchemaTypes(readContent(path), outputRoot, basePackage);
    }

    public static List<Path> generateSchemaTypes(String content, Path outputRoot, String basePackage) {
        return StepSchemaEngine.generateJavaTypes(parse(content), outputRoot, basePackage);
    }

    private static String readContent(Path path) throws IOException {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (MalformedInputException exception) {
            return Files.readString(path, StandardCharsets.ISO_8859_1);
        }
    }
}
