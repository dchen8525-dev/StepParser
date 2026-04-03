package com.example.stepparser.schema;

import com.example.stepparser.model.StepEntityInstance;
import com.example.stepparser.model.StepFile;
import com.example.stepparser.model.StepListValue;
import com.example.stepparser.model.StepOmittedValue;
import com.example.stepparser.model.StepReferenceValue;
import com.example.stepparser.model.StepSchemas;
import com.example.stepparser.model.StepStringValue;
import com.example.stepparser.model.StepValue;
import com.example.stepparser.schema.runtime.StepSchemaModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StepSemanticMapper {

    private StepSemanticMapper() {
    }

    public static StepSemanticModel map(StepFile file) {
        StepSchemaModel schemaModel = StepSchemaEngine.map(file);
        Map<Integer, StepEntityInstance> byId = file.data().entities().stream()
                .collect(Collectors.toMap(StepEntityInstance::id, Function.identity()));

        List<StepApplicationContext> applicationContexts = file.data().entities().stream()
                .filter(entity -> entity.type().equals("APPLICATION_CONTEXT"))
                .map(StepSemanticMapper::mapApplicationContext)
                .toList();

        Map<Integer, StepApplicationContext> contextsById = applicationContexts.stream()
                .collect(Collectors.toMap(StepApplicationContext::entityId, Function.identity()));

        List<StepProduct> products = file.data().entities().stream()
                .filter(entity -> entity.type().equals("PRODUCT"))
                .map(entity -> mapProduct(entity, byId, contextsById))
                .toList();

        return new StepSemanticModel(
                file,
                StepSchemas.detect(file.header().schemaNames()),
                schemaModel,
                applicationContexts,
                products
        );
    }

    private static StepApplicationContext mapApplicationContext(StepEntityInstance entity) {
        if (entity.parameters().size() != 1) {
            throw new StepSemanticException("APPLICATION_CONTEXT #" + entity.id() + " must have exactly 1 parameter");
        }
        return new StepApplicationContext(entity.id(), requireString(entity.parameters().getFirst(), "APPLICATION_CONTEXT.description"));
    }

    private static StepProduct mapProduct(
            StepEntityInstance entity,
            Map<Integer, StepEntityInstance> byId,
            Map<Integer, StepApplicationContext> contextsById
    ) {
        if (entity.parameters().size() != 4) {
            throw new StepSemanticException("PRODUCT #" + entity.id() + " must have exactly 4 parameters");
        }

        String id = requireString(entity.parameters().get(0), "PRODUCT.id");
        String name = requireString(entity.parameters().get(1), "PRODUCT.name");
        String description = readOptionalString(entity.parameters().get(2));
        List<StepApplicationContext> contexts = mapProductContexts(entity.parameters().get(3), byId, contextsById, entity.id());

        return new StepProduct(entity.id(), id, name, description, contexts);
    }

    private static List<StepApplicationContext> mapProductContexts(
            StepValue value,
            Map<Integer, StepEntityInstance> byId,
            Map<Integer, StepApplicationContext> contextsById,
            int productEntityId
    ) {
        if (value instanceof StepOmittedValue) {
            return List.of();
        }
        if (!(value instanceof StepListValue listValue)) {
            throw new StepSemanticException("PRODUCT #" + productEntityId + " context parameter must be an aggregate");
        }

        List<StepApplicationContext> contexts = new ArrayList<>();
        for (StepValue entry : listValue.values()) {
            if (!(entry instanceof StepReferenceValue referenceValue)) {
                throw new StepSemanticException("PRODUCT #" + productEntityId + " context aggregate must contain references");
            }
            StepEntityInstance target = referenceValue.target();
            if (target == null) {
                throw new StepSemanticException("PRODUCT #" + productEntityId + " references missing entity #" + referenceValue.referenceId());
            }

            StepApplicationContext context = contextsById.get(target.id());
            if (context != null) {
                contexts.add(context);
                continue;
            }

            StepEntityInstance rawContext = byId.get(target.id());
            if (rawContext != null && rawContext.type().equals("APPLICATION_CONTEXT")) {
                contexts.add(mapApplicationContext(rawContext));
                continue;
            }

            throw new StepSemanticException("PRODUCT #" + productEntityId + " references non-APPLICATION_CONTEXT entity #" + target.id());
        }
        return List.copyOf(contexts);
    }

    private static String requireString(StepValue value, String fieldName) {
        if (value instanceof StepStringValue stringValue) {
            return stringValue.value();
        }
        throw new StepSemanticException(fieldName + " must be a STEP string");
    }

    private static String readOptionalString(StepValue value) {
        if (value instanceof StepOmittedValue) {
            return null;
        }
        return requireString(value, "optional string");
    }
}
