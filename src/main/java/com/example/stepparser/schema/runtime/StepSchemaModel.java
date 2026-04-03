package com.example.stepparser.schema.runtime;

import com.example.stepparser.schema.meta.StepSchemaDefinition;

import java.util.List;
import java.util.Optional;

public record StepSchemaModel(
        StepSchemaDefinition schemaDefinition,
        List<StepTypedEntity> entities
) {

    public StepSchemaModel {
        entities = List.copyOf(entities);
    }

    public Optional<StepTypedEntity> findEntity(int entityId) {
        return entities.stream()
                .filter(entity -> entity.entityId() == entityId)
                .findFirst();
    }

    public List<StepEntityReference> inverseReferences(int entityId, String inverseAttributeName) {
        return findEntity(entityId)
                .map(entity -> entity.inverseAttributes().getOrDefault(inverseAttributeName, List.of()))
                .orElse(List.of());
    }
}
