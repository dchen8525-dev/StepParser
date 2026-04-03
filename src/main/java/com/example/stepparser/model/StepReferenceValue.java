package com.example.stepparser.model;

public record StepReferenceValue(int referenceId, StepEntityInstance target) implements StepValue {

    public StepReferenceValue(int referenceId) {
        this(referenceId, null);
    }

    public StepReferenceValue withTarget(StepEntityInstance resolvedTarget) {
        return new StepReferenceValue(referenceId, resolvedTarget);
    }
}
