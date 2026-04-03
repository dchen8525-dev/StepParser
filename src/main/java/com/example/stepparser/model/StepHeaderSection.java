package com.example.stepparser.model;

import java.util.List;

public record StepHeaderSection(List<StepHeaderEntity> entities) {

    public StepHeaderSection {
        entities = List.copyOf(entities);
    }

    public List<String> schemaNames() {
        return entities.stream()
                .filter(entity -> entity.name().equalsIgnoreCase("FILE_SCHEMA"))
                .findFirst()
                .map(entity -> entity.parameters().stream()
                        .filter(StepListValue.class::isInstance)
                        .map(StepListValue.class::cast)
                        .flatMap(list -> list.values().stream())
                        .filter(StepStringValue.class::isInstance)
                        .map(StepStringValue.class::cast)
                        .map(StepStringValue::value)
                        .toList())
                .orElse(List.of());
    }
}
