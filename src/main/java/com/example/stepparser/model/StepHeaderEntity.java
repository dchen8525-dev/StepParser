package com.example.stepparser.model;

import java.util.List;

public record StepHeaderEntity(String name, List<StepValue> parameters) {

    public StepHeaderEntity {
        parameters = List.copyOf(parameters);
    }
}
