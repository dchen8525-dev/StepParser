package com.example.stepparser.schema.meta;

import java.util.List;

public record StepTypeRef(
        StepTypeKind kind,
        StepTypeRef itemType,
        String targetEntityName,
        List<String> options,
        Integer minSize,
        Integer maxSize
) {

    public static StepTypeRef stringType() {
        return new StepTypeRef(StepTypeKind.STRING, null, null, List.of(), null, null);
    }

    public static StepTypeRef numberType() {
        return new StepTypeRef(StepTypeKind.NUMBER, null, null, List.of(), null, null);
    }

    public static StepTypeRef enumType(List<String> options) {
        return new StepTypeRef(StepTypeKind.ENUM, null, null, options.stream().map(String::toUpperCase).toList(), null, null);
    }

    public static StepTypeRef genericType() {
        return new StepTypeRef(StepTypeKind.GENERIC, null, null, List.of(), null, null);
    }

    public static StepTypeRef referenceType(String targetEntityName) {
        return new StepTypeRef(StepTypeKind.REFERENCE, null, targetEntityName.toUpperCase(), List.of(), null, null);
    }

    public static StepTypeRef listOf(StepTypeRef itemType) {
        return new StepTypeRef(StepTypeKind.LIST, itemType, null, List.of(), null, null);
    }

    public static StepTypeRef boundedListOf(StepTypeRef itemType, Integer minSize, Integer maxSize) {
        return new StepTypeRef(StepTypeKind.LIST, itemType, null, List.of(), minSize, maxSize);
    }

    public static StepTypeRef selectType(List<String> options) {
        return new StepTypeRef(StepTypeKind.SELECT, null, null, options.stream().map(String::toUpperCase).toList(), null, null);
    }
}
