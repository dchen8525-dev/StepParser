package com.example.stepparser.schema;

import java.util.List;

public record StepProduct(
        int entityId,
        String id,
        String name,
        String description,
        List<StepApplicationContext> contexts
) {
}
