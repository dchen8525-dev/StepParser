package com.example.stepparser.schema.meta;

import java.util.List;

public record StepEntityDefinition(
        String name,
        boolean isAbstract,
        String supertype,
        List<StepAttributeDefinition> attributes
) {

    public StepEntityDefinition {
        attributes = List.copyOf(attributes);
    }
}
