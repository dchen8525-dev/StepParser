package com.example.stepparser.model;

import java.util.List;

public record StepTypedValue(String type, List<StepValue> arguments) implements StepValue {

    public StepTypedValue {
        arguments = List.copyOf(arguments);
    }
}
