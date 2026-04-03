package com.example.stepparser.resolve;

import com.example.stepparser.model.StepDataSection;
import com.example.stepparser.model.StepDerivedValue;
import com.example.stepparser.model.StepEntityInstance;
import com.example.stepparser.model.StepEnumValue;
import com.example.stepparser.model.StepFile;
import com.example.stepparser.model.StepHeaderEntity;
import com.example.stepparser.model.StepHeaderSection;
import com.example.stepparser.model.StepListValue;
import com.example.stepparser.model.StepNumberValue;
import com.example.stepparser.model.StepOmittedValue;
import com.example.stepparser.model.StepReferenceValue;
import com.example.stepparser.model.StepStringValue;
import com.example.stepparser.model.StepTypedValue;
import com.example.stepparser.model.StepValue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StepReferenceResolver {

    private StepReferenceResolver() {
    }

    public static StepFile resolve(StepFile file) {
        Map<Integer, StepEntityInstance> entitiesById = file.data().entities().stream()
                .collect(Collectors.toMap(StepEntityInstance::id, Function.identity()));

        List<StepHeaderEntity> headerEntities = file.header().entities().stream()
                .map(entity -> new StepHeaderEntity(entity.name(), resolveValues(entity.parameters(), entitiesById)))
                .toList();

        List<StepEntityInstance> dataEntities = file.data().entities().stream()
                .map(entity -> new StepEntityInstance(entity.id(), entity.type(), resolveValues(entity.parameters(), entitiesById)))
                .toList();

        return new StepFile(new StepHeaderSection(headerEntities), new StepDataSection(dataEntities));
    }

    private static List<StepValue> resolveValues(List<StepValue> values, Map<Integer, StepEntityInstance> entitiesById) {
        return values.stream().map(value -> resolveValue(value, entitiesById)).toList();
    }

    private static StepValue resolveValue(StepValue value, Map<Integer, StepEntityInstance> entitiesById) {
        return switch (value) {
            case StepReferenceValue referenceValue -> referenceValue.withTarget(entitiesById.get(referenceValue.referenceId()));
            case StepListValue listValue -> new StepListValue(resolveValues(listValue.values(), entitiesById));
            case StepTypedValue typedValue -> new StepTypedValue(typedValue.type(), resolveValues(typedValue.arguments(), entitiesById));
            case StepStringValue stringValue -> stringValue;
            case StepNumberValue numberValue -> numberValue;
            case StepEnumValue enumValue -> enumValue;
            case StepOmittedValue omittedValue -> omittedValue;
            case StepDerivedValue derivedValue -> derivedValue;
        };
    }
}
