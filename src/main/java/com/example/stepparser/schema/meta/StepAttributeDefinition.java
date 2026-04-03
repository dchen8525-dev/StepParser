package com.example.stepparser.schema.meta;

public record StepAttributeDefinition(
        String name,
        StepTypeRef type,
        StepCardinality cardinality,
        StepAttributeKind kind
) {
}
