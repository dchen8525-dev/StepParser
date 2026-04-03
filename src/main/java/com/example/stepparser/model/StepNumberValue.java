package com.example.stepparser.model;

import java.math.BigDecimal;

public record StepNumberValue(BigDecimal value) implements StepValue {
}
