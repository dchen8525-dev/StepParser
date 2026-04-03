package com.example.stepparser.model;

import java.util.List;

public record StepEntityInstance(int id, String type, List<StepValue> parameters) {

    public StepEntityInstance {
        parameters = List.copyOf(parameters);
    }
}
