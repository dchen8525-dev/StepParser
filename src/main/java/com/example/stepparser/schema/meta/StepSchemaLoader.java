package com.example.stepparser.schema.meta;

import com.example.stepparser.schema.StepSemanticException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StepSchemaLoader {

    private static final Map<String, String> BUILTIN_SCHEMAS = Map.of(
            "AP203", "/schemas/ap203.schema",
            "AP214", "/schemas/ap214.schema",
            "AP242", "/schemas/ap242.schema"
    );

    private StepSchemaLoader() {
    }

    public static StepSchemaDefinition loadBuiltin(String schemaFamily) {
        String resourcePath = BUILTIN_SCHEMAS.get(schemaFamily.toUpperCase(Locale.ROOT));
        if (resourcePath == null) {
            throw new StepSemanticException("No built-in schema metadata for " + schemaFamily);
        }
        try (InputStream inputStream = StepSchemaLoader.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new StepSemanticException("Schema resource not found: " + resourcePath);
            }
            return parse(new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .toList());
        } catch (IOException exception) {
            throw new StepSemanticException("Failed to load schema resource " + resourcePath + ": " + exception.getMessage());
        }
    }

    static StepSchemaDefinition parse(List<String> lines) {
        String schemaName = null;
        List<StepEntityDefinition> entities = new ArrayList<>();
        String currentEntityName = null;
        boolean currentAbstract = false;
        String currentSupertype = null;
        List<StepAttributeDefinition> currentAttributes = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("\\s+");
            switch (parts[0]) {
                case "SCHEMA" -> schemaName = requireLength(parts, 2, line)[1].toUpperCase(Locale.ROOT);
                case "ENTITY" -> {
                    ensureNoOpenEntity(currentEntityName);
                    int nameIndex = 1;
                    currentAbstract = false;
                    if (parts.length >= 3 && parts[1].equals("ABSTRACT")) {
                        currentAbstract = true;
                        nameIndex = 2;
                    }
                    currentEntityName = requireLength(parts, nameIndex + 1, line)[nameIndex].toUpperCase(Locale.ROOT);
                    currentSupertype = null;
                    if (parts.length >= nameIndex + 3 && parts[nameIndex + 1].equals("SUBTYPE_OF")) {
                        currentSupertype = parts[nameIndex + 2].toUpperCase(Locale.ROOT);
                    }
                    currentAttributes = new ArrayList<>();
                }
                case "ATTRIBUTE" -> {
                    if (currentEntityName == null) {
                        throw new StepSemanticException("ATTRIBUTE outside ENTITY: " + line);
                    }
                    currentAttributes.add(parseAttribute(parts, line, StepAttributeKind.EXPLICIT));
                }
                case "DERIVED" -> {
                    if (currentEntityName == null) {
                        throw new StepSemanticException("DERIVED outside ENTITY: " + line);
                    }
                    currentAttributes.add(parseAttribute(parts, line, StepAttributeKind.DERIVED));
                }
                case "INVERSE" -> {
                    if (currentEntityName == null) {
                        throw new StepSemanticException("INVERSE outside ENTITY: " + line);
                    }
                    currentAttributes.add(parseAttribute(parts, line, StepAttributeKind.INVERSE));
                }
                case "END_ENTITY" -> {
                    if (currentEntityName == null) {
                        throw new StepSemanticException("END_ENTITY without ENTITY");
                    }
                    entities.add(new StepEntityDefinition(currentEntityName, currentAbstract, currentSupertype, currentAttributes));
                    currentEntityName = null;
                    currentAbstract = false;
                    currentSupertype = null;
                }
                case "END_SCHEMA" -> {
                    if (schemaName == null) {
                        throw new StepSemanticException("END_SCHEMA without SCHEMA");
                    }
                    if (currentEntityName != null) {
                        throw new StepSemanticException("Unclosed ENTITY " + currentEntityName);
                    }
                    return new StepSchemaDefinition(schemaName, entities);
                }
                default -> throw new StepSemanticException("Unknown schema directive: " + parts[0]);
            }
        }

        throw new StepSemanticException("Schema definition missing END_SCHEMA");
    }

    private static StepAttributeDefinition parseAttribute(String[] parts, String line, StepAttributeKind kind) {
        if (kind == StepAttributeKind.EXPLICIT && parts.length < 4) {
            throw new StepSemanticException("Invalid ATTRIBUTE declaration: " + line);
        }
        if (kind != StepAttributeKind.EXPLICIT && parts.length < 3) {
            throw new StepSemanticException("Invalid ATTRIBUTE declaration: " + line);
        }

        String attributeName = parts[1];
        int[] index = {2};
        StepTypeRef type = parseType(parts, index, line);

        StepCardinality cardinality = StepCardinality.OPTIONAL;
        if (kind == StepAttributeKind.EXPLICIT) {
            if (index[0] >= parts.length) {
                throw new StepSemanticException("Missing cardinality in ATTRIBUTE declaration: " + line);
            }

            cardinality = switch (parts[index[0]]) {
                case "REQUIRED" -> StepCardinality.REQUIRED;
                case "OPTIONAL" -> StepCardinality.OPTIONAL;
                default -> throw new StepSemanticException("Invalid cardinality in ATTRIBUTE declaration: " + line);
            };
        }
        return new StepAttributeDefinition(attributeName, type, cardinality, kind);
    }

    private static StepTypeRef parseType(String[] parts, int[] index, String line) {
        if (index[0] >= parts.length) {
            throw new StepSemanticException("Missing type in ATTRIBUTE declaration: " + line);
        }

        return switch (parts[index[0]++]) {
            case "STRING" -> StepTypeRef.stringType();
            case "NUMBER" -> StepTypeRef.numberType();
            case "ENUM" -> {
                if (index[0] >= parts.length) {
                    throw new StepSemanticException("ENUM must declare at least one option: " + line);
                }
                yield StepTypeRef.enumType(parseOptions(parts[index[0]++], line));
            }
            case "GENERIC" -> StepTypeRef.genericType();
            case "REFERENCE" -> {
                if (index[0] >= parts.length) {
                    throw new StepSemanticException("REFERENCE must name a target entity: " + line);
                }
                yield StepTypeRef.referenceType(parts[index[0]++]);
            }
            case "LIST" -> {
                Integer minSize = null;
                Integer maxSize = null;
                if (index[0] < parts.length && parts[index[0]].startsWith("[")) {
                    String boundsToken = parts[index[0]++];
                    int separator = boundsToken.indexOf("..");
                    if (!boundsToken.endsWith("]") || separator < 0) {
                        throw new StepSemanticException("Invalid LIST bounds in declaration: " + line);
                    }
                    String minToken = boundsToken.substring(1, separator);
                    String maxToken = boundsToken.substring(separator + 2, boundsToken.length() - 1);
                    minSize = minToken.equals("*") ? null : Integer.parseInt(minToken);
                    maxSize = maxToken.equals("*") ? null : Integer.parseInt(maxToken);
                }
                StepTypeRef itemType = parseType(parts, index, line);
                yield StepTypeRef.boundedListOf(itemType, minSize, maxSize);
            }
            case "SELECT" -> {
                if (index[0] >= parts.length) {
                    throw new StepSemanticException("SELECT must declare at least one option: " + line);
                }
                yield StepTypeRef.selectType(parseOptions(parts[index[0]++], line));
            }
            default -> throw new StepSemanticException("Unsupported attribute type in declaration: " + line);
        };
    }

    private static List<String> parseOptions(String token, String line) {
        if (!token.startsWith("(") || !token.endsWith(")")) {
            throw new StepSemanticException("Expected parenthesized option list in declaration: " + line);
        }
        String body = token.substring(1, token.length() - 1).trim();
        if (body.isEmpty()) {
            throw new StepSemanticException("Option list cannot be empty: " + line);
        }
        return java.util.Arrays.stream(body.split("\\|"))
                .map(String::trim)
                .filter(option -> !option.isEmpty())
                .toList();
    }

    private static void ensureNoOpenEntity(String currentEntityName) {
        if (currentEntityName != null) {
            throw new StepSemanticException("Nested ENTITY not allowed while parsing " + currentEntityName);
        }
    }

    private static String[] requireLength(String[] parts, int expected, String line) {
        if (parts.length < expected) {
            throw new StepSemanticException("Invalid schema declaration: " + line);
        }
        return parts;
    }
}
