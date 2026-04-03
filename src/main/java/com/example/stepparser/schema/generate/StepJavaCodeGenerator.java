package com.example.stepparser.schema.generate;

import com.example.stepparser.schema.StepSemanticException;
import com.example.stepparser.schema.meta.StepAttributeDefinition;
import com.example.stepparser.schema.meta.StepAttributeKind;
import com.example.stepparser.schema.meta.StepEntityDefinition;
import com.example.stepparser.schema.meta.StepSchemaDefinition;
import com.example.stepparser.schema.meta.StepTypeKind;
import com.example.stepparser.schema.meta.StepTypeRef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class StepJavaCodeGenerator {

    private StepJavaCodeGenerator() {
    }

    public static List<Path> generate(StepSchemaDefinition schemaDefinition, Path outputRoot, String basePackage) {
        List<Path> generatedFiles = new ArrayList<>();
        String packageName = basePackage + "." + schemaDefinition.name().toLowerCase();
        Path packageRoot = outputRoot.resolve(packageName.replace('.', '/'));

        try {
            Files.createDirectories(packageRoot);
        } catch (IOException exception) {
            throw new StepSemanticException("Failed to create output directory " + packageRoot + ": " + exception.getMessage());
        }

        for (StepEntityDefinition entity : schemaDefinition.entities()) {
            String typeName = JavaNameSanitizer.toTypeName(entity.name());
            Path file = packageRoot.resolve(typeName + ".java");
            String source = renderEntity(packageName, typeName, entity);
            try {
                Files.writeString(file, source);
            } catch (IOException exception) {
                throw new StepSemanticException("Failed to write generated source " + file + ": " + exception.getMessage());
            }
            generatedFiles.add(file);
        }

        return List.copyOf(generatedFiles);
    }

    private static String renderEntity(String packageName, String typeName, StepEntityDefinition entity) {
        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(packageName).append(";\n\n");

        List<StepAttributeDefinition> explicitAttributes = entity.attributes().stream()
                .filter(attribute -> attribute.kind() == StepAttributeKind.EXPLICIT)
                .toList();

        boolean needsList = explicitAttributes.stream().anyMatch(attribute -> attribute.type().kind() == StepTypeKind.LIST);
        if (needsList) {
            builder.append("import java.util.List;\n\n");
        }

        if (entity.isAbstract()) {
            builder.append("// Abstract entity\n");
        }
        if (entity.supertype() != null) {
            builder.append("// Subtype of ").append(entity.supertype()).append("\n");
        }
        builder.append("public record ").append(typeName).append("(\n");
        for (int index = 0; index < explicitAttributes.size(); index++) {
            StepAttributeDefinition attribute = explicitAttributes.get(index);
            builder.append("        ")
                    .append(javaType(attribute.type()))
                    .append(" ")
                    .append(JavaNameSanitizer.toFieldName(attribute.name()));
            if (index < explicitAttributes.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }
        builder.append(") {\n}\n");
        return builder.toString();
    }

    private static String javaType(StepTypeRef type) {
        return switch (type.kind()) {
            case STRING, ENUM -> "String";
            case NUMBER -> "java.math.BigDecimal";
            case GENERIC -> "Object";
            case REFERENCE -> "Integer";
            case LIST -> "List<" + boxedJavaType(type.itemType()) + ">";
            case SELECT -> "Object";
        };
    }

    private static String boxedJavaType(StepTypeRef type) {
        return switch (type.kind()) {
            case STRING, ENUM -> "String";
            case NUMBER -> "java.math.BigDecimal";
            case GENERIC -> "Object";
            case REFERENCE -> "Integer";
            case LIST -> "List<" + boxedJavaType(type.itemType()) + ">";
            case SELECT -> "Object";
        };
    }
}
