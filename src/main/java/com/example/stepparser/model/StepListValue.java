package com.example.stepparser.model;

import java.util.List;

public record StepListValue(List<StepValue> values) implements StepValue {

    public StepListValue {
        values = List.copyOf(values);
    }
}
