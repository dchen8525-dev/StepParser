package com.example.stepparser.schema.runtime;

import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;

public record StepTypedEntity(
        int entityId,
        String entityName,
        Map<String, Object> attributes,
        Map<String, List<StepEntityReference>> inverseAttributes
) {

    public StepTypedEntity {
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        inverseAttributes = inverseAttributes.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
    }
}
