package com.example.stepparser.model;

import java.util.List;
import java.util.Optional;

public record StepDataSection(List<StepEntityInstance> entities) {

    public StepDataSection {
        entities = List.copyOf(entities);
    }

    public Optional<StepEntityInstance> findById(int id) {
        return entities.stream().filter(entity -> entity.id() == id).findFirst();
    }
}
