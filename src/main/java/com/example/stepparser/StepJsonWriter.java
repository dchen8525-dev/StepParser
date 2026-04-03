package com.example.stepparser;

import java.util.Iterator;

public final class StepJsonWriter {

    private StepJsonWriter() {
    }

    public static String writeScene(StepAssemblyScene scene) {
        StringBuilder builder = new StringBuilder(1024);
        builder.append('{');
        field(builder, "sourceStepFile", scene.sourceStepFile()).append(',');
        field(builder, "schemaNames", scene.schemaNames()).append(',');
        fieldName(builder, "roots");
        writeNodes(builder, scene.roots());
        builder.append(',');
        field(builder, "warnings", scene.warnings());
        builder.append('}');
        return builder.toString();
    }

    private static void writeNodes(StringBuilder builder, Iterable<StepAssemblyScene.SceneNode> nodes) {
        builder.append('[');
        Iterator<StepAssemblyScene.SceneNode> iterator = nodes.iterator();
        while (iterator.hasNext()) {
            writeNode(builder, iterator.next());
            if (iterator.hasNext()) {
                builder.append(',');
            }
        }
        builder.append(']');
    }

    private static void writeNode(StringBuilder builder, StepAssemblyScene.SceneNode node) {
        builder.append('{');
        field(builder, "instanceId", node.instanceId()).append(',');
        nullableField(builder, "occurrenceEntityId", node.occurrenceEntityId()).append(',');
        nullableField(builder, "occurrenceId", node.occurrenceId()).append(',');
        numericField(builder, "definitionId", node.definitionId()).append(',');
        nullableField(builder, "formationEntityId", node.formationEntityId()).append(',');
        nullableField(builder, "productEntityId", node.productEntityId()).append(',');
        nullableField(builder, "productId", node.productId()).append(',');
        nullableField(builder, "name", node.name()).append(',');
        nullableField(builder, "description", node.description()).append(',');
        field(builder, "displayName", node.displayName()).append(',');
        fieldName(builder, "glb");
        writeAsset(builder, node.glb());
        builder.append(',');
        fieldName(builder, "children");
        writeNodes(builder, node.children());
        builder.append('}');
    }

    private static void writeAsset(StringBuilder builder, StepAssemblyScene.GlbAsset asset) {
        builder.append('{');
        field(builder, "fileName", asset.fileName()).append(',');
        nullableField(builder, "relativeUri", asset.relativeUri()).append(',');
        booleanField(builder, "exported", asset.exported()).append(',');
        nullableField(builder, "error", asset.error());
        builder.append('}');
    }

    private static StringBuilder field(StringBuilder builder, String name, String value) {
        fieldName(builder, name);
        writeString(builder, value);
        return builder;
    }

    private static StringBuilder field(StringBuilder builder, String name, Iterable<String> values) {
        fieldName(builder, name);
        builder.append('[');
        Iterator<String> iterator = values.iterator();
        while (iterator.hasNext()) {
            writeString(builder, iterator.next());
            if (iterator.hasNext()) {
                builder.append(',');
            }
        }
        builder.append(']');
        return builder;
    }

    private static StringBuilder nullableField(StringBuilder builder, String name, String value) {
        fieldName(builder, name);
        if (value == null) {
            builder.append("null");
        } else {
            writeString(builder, value);
        }
        return builder;
    }

    private static StringBuilder nullableField(StringBuilder builder, String name, Integer value) {
        fieldName(builder, name);
        if (value == null) {
            builder.append("null");
        } else {
            builder.append(value);
        }
        return builder;
    }

    private static StringBuilder numericField(StringBuilder builder, String name, int value) {
        fieldName(builder, name);
        builder.append(value);
        return builder;
    }

    private static StringBuilder booleanField(StringBuilder builder, String name, boolean value) {
        fieldName(builder, name);
        builder.append(value);
        return builder;
    }

    private static void fieldName(StringBuilder builder, String name) {
        writeString(builder, name);
        builder.append(':');
    }

    private static void writeString(StringBuilder builder, String value) {
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');
    }
}
