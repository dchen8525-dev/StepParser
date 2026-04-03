package com.example.stepparser.schema.runtime;

public record StepEntityReference(
        int referenceId,
        String targetEntityType
) {
}
