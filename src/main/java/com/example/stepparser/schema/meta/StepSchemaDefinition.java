package com.example.stepparser.schema.meta;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public record StepSchemaDefinition(
        String name,
        List<StepEntityDefinition> entities
) {

    public StepSchemaDefinition {
        entities = List.copyOf(entities);
    }

    public Optional<StepEntityDefinition> findEntity(String entityName) {
        return entities.stream()
                .filter(entity -> entity.name().equalsIgnoreCase(entityName))
                .findFirst();
    }

    public List<StepAttributeDefinition> resolveAttributes(String entityName) {
        StepEntityDefinition entity = findEntity(entityName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown entity " + entityName));
        return resolveAttributes(entity);
    }

    public List<StepAttributeDefinition> resolveExplicitAttributes(String entityName) {
        return resolveAttributes(entityName).stream()
                .filter(attribute -> attribute.kind() == StepAttributeKind.EXPLICIT)
                .toList();
    }

    public boolean isAssignable(String expectedEntityName, String actualEntityName) {
        if (expectedEntityName.equalsIgnoreCase(actualEntityName)) {
            return true;
        }
        StepEntityDefinition actual = findEntity(actualEntityName).orElse(null);
        while (actual != null && actual.supertype() != null) {
            if (actual.supertype().equalsIgnoreCase(expectedEntityName)) {
                return true;
            }
            actual = findEntity(actual.supertype()).orElse(null);
        }
        return false;
    }

    private List<StepAttributeDefinition> resolveAttributes(StepEntityDefinition entity) {
        if (entity.supertype() == null) {
            return entity.attributes();
        }
        return java.util.stream.Stream.concat(
                resolveAttributes(entity.supertype()).stream(),
                entity.attributes().stream()
        ).toList();
    }

    public Map<String, StepEntityDefinition> entityMap() {
        return entities.stream()
                .collect(Collectors.toMap(entity -> entity.name().toUpperCase(), Function.identity()));
    }
}
