package com.example.stepparser.schema.runtime;

import com.example.stepparser.model.StepDerivedValue;
import com.example.stepparser.model.StepEntityInstance;
import com.example.stepparser.model.StepEnumValue;
import com.example.stepparser.model.StepFile;
import com.example.stepparser.model.StepListValue;
import com.example.stepparser.model.StepNumberValue;
import com.example.stepparser.model.StepOmittedValue;
import com.example.stepparser.model.StepReferenceValue;
import com.example.stepparser.model.StepStringValue;
import com.example.stepparser.model.StepTypedValue;
import com.example.stepparser.model.StepValue;
import com.example.stepparser.schema.StepSemanticException;
import com.example.stepparser.schema.meta.StepAttributeDefinition;
import com.example.stepparser.schema.meta.StepAttributeKind;
import com.example.stepparser.schema.meta.StepCardinality;
import com.example.stepparser.schema.meta.StepEntityDefinition;
import com.example.stepparser.schema.meta.StepSchemaDefinition;
import com.example.stepparser.schema.meta.StepTypeKind;
import com.example.stepparser.schema.meta.StepTypeRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StepSchemaRuntimeMapper {

    private StepSchemaRuntimeMapper() {
    }

    public static StepSchemaModel map(StepFile file, StepSchemaDefinition schemaDefinition) {
        Map<Integer, StepEntityInstance> entitiesById = file.data().entities().stream()
                .collect(Collectors.toMap(StepEntityInstance::id, Function.identity()));
        List<StepTypedEntity> typedEntities = new ArrayList<>();

        for (StepEntityInstance entity : file.data().entities()) {
            StepEntityDefinition definition = schemaDefinition.findEntity(entity.type())
                    .orElse(null);
            if (definition == null) {
                continue;
            }
            if (definition.isAbstract()) {
                throw new StepSemanticException("Abstract entity " + entity.type() + " cannot be instantiated directly");
            }
            typedEntities.add(mapEntity(entity, definition, schemaDefinition, entitiesById));
        }

        return new StepSchemaModel(schemaDefinition, attachInverseAttributes(typedEntities, schemaDefinition));
    }

    private static StepTypedEntity mapEntity(
            StepEntityInstance entity,
            StepEntityDefinition definition,
            StepSchemaDefinition schemaDefinition,
            Map<Integer, StepEntityInstance> entitiesById
    ) {
        List<StepAttributeDefinition> attributes = schemaDefinition.resolveExplicitAttributes(definition.name());
        if (entity.parameters().size() != attributes.size()) {
            throw new StepSemanticException(
                    "Entity #" + entity.id() + " " + entity.type() + " expected " + attributes.size()
                            + " attributes but found " + entity.parameters().size()
            );
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < attributes.size(); index++) {
            StepAttributeDefinition attribute = attributes.get(index);
            values.put(attribute.name(), mapValue(entity, attribute, entity.parameters().get(index), entitiesById, schemaDefinition));
        }
        return new StepTypedEntity(entity.id(), entity.type(), Collections.unmodifiableMap(new LinkedHashMap<>(values)), Map.of());
    }

    private static List<StepTypedEntity> attachInverseAttributes(
            List<StepTypedEntity> entities,
            StepSchemaDefinition schemaDefinition
    ) {
        Map<Integer, Map<String, List<StepEntityReference>>> inverseIndex = new HashMap<>();
        for (StepTypedEntity entity : entities) {
            inverseIndex.put(entity.entityId(), new LinkedHashMap<>());
        }

        for (StepTypedEntity source : entities) {
            for (Object value : source.attributes().values()) {
                collectInverseReferences(source, value, inverseIndex);
            }
        }

        List<StepTypedEntity> enriched = new ArrayList<>();
        for (StepTypedEntity entity : entities) {
            Map<String, List<StepEntityReference>> inverseAttributes = new LinkedHashMap<>();
            for (StepAttributeDefinition attribute : schemaDefinition.resolveAttributes(entity.entityName())) {
                if (attribute.kind() != StepAttributeKind.INVERSE) {
                    continue;
                }
                inverseAttributes.put(
                        attribute.name(),
                        inverseIndex.getOrDefault(entity.entityId(), Map.of()).getOrDefault(attribute.name(), List.of())
                );
            }
            enriched.add(new StepTypedEntity(entity.entityId(), entity.entityName(), entity.attributes(), inverseAttributes));
        }
        return List.copyOf(enriched);
    }

    private static void collectInverseReferences(
            StepTypedEntity source,
            Object value,
            Map<Integer, Map<String, List<StepEntityReference>>> inverseIndex
    ) {
        if (value instanceof StepEntityReference reference) {
            inverseIndex.computeIfAbsent(reference.referenceId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent("items_from_representation", ignored -> new ArrayList<>())
                    .add(new StepEntityReference(source.entityId(), source.entityName()));
            inverseIndex.computeIfAbsent(reference.referenceId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent("representation_name", ignored -> new ArrayList<>());
            return;
        }
        if (value instanceof List<?> list) {
            for (Object entry : list) {
                collectInverseReferences(source, entry, inverseIndex);
            }
        }
    }

    private static Object mapValue(
            StepEntityInstance owner,
            StepAttributeDefinition attribute,
            StepValue value,
            Map<Integer, StepEntityInstance> entitiesById,
            StepSchemaDefinition schemaDefinition
    ) {
        if (value instanceof StepOmittedValue) {
            if (attribute.cardinality() == StepCardinality.REQUIRED) {
                throw new StepSemanticException("Required attribute " + owner.type() + "." + attribute.name() + " is omitted");
            }
            return null;
        }

        if (value instanceof StepDerivedValue) {
            throw new StepSemanticException("Derived value '*' is not allowed for explicit attribute " + owner.type() + "." + attribute.name());
        }

        return mapNonNullValue(owner, attribute, value, entitiesById, schemaDefinition);
    }

    private static Object mapNonNullValue(
            StepEntityInstance owner,
            StepAttributeDefinition attribute,
            StepValue value,
            Map<Integer, StepEntityInstance> entitiesById,
            StepSchemaDefinition schemaDefinition
    ) {
        StepTypeRef type = attribute.type();
        return switch (type.kind()) {
            case STRING -> requireString(owner, attribute, value);
            case NUMBER -> requireNumber(owner, attribute, value);
            case ENUM -> requireEnum(owner, attribute, value);
            case GENERIC -> mapGenericValue(value, entitiesById);
            case REFERENCE -> requireReference(owner, attribute, value, entitiesById, schemaDefinition, type.targetEntityName());
            case LIST -> requireList(owner, attribute, value, entitiesById, schemaDefinition, type.itemType());
            case SELECT -> requireSelect(owner, attribute, value, entitiesById, schemaDefinition, type.options());
        };
    }

    private static String requireString(StepEntityInstance owner, StepAttributeDefinition attribute, StepValue value) {
        if (value instanceof StepStringValue stringValue) {
            return stringValue.value();
        }
        throw wrongType(owner, attribute, "STRING", value);
    }

    private static java.math.BigDecimal requireNumber(StepEntityInstance owner, StepAttributeDefinition attribute, StepValue value) {
        if (value instanceof StepNumberValue numberValue) {
            return numberValue.value();
        }
        throw wrongType(owner, attribute, "NUMBER", value);
    }

    private static String requireEnum(StepEntityInstance owner, StepAttributeDefinition attribute, StepValue value) {
        if (value instanceof StepEnumValue enumValue) {
            if (!attribute.type().options().isEmpty() && !attribute.type().options().contains(enumValue.value().toUpperCase())) {
                throw new StepSemanticException(
                        "Entity #" + owner.id() + " " + owner.type() + "." + attribute.name()
                                + " enum value " + enumValue.value() + " is not in " + attribute.type().options()
                );
            }
            return enumValue.value();
        }
        throw wrongType(owner, attribute, "ENUM", value);
    }

    private static StepEntityReference requireReference(
            StepEntityInstance owner,
            StepAttributeDefinition attribute,
            StepValue value,
            Map<Integer, StepEntityInstance> entitiesById,
            StepSchemaDefinition schemaDefinition,
            String targetEntityName
    ) {
        if (!(value instanceof StepReferenceValue referenceValue)) {
            throw wrongType(owner, attribute, "REFERENCE " + targetEntityName, value);
        }
        StepEntityInstance target = referenceValue.target();
        if (target == null) {
            target = entitiesById.get(referenceValue.referenceId());
        }
        if (target == null) {
            throw new StepSemanticException(
                    "Entity #" + owner.id() + " " + owner.type() + "." + attribute.name()
                            + " references missing entity #" + referenceValue.referenceId()
            );
        }
        if (targetEntityName != null && !schemaDefinition.isAssignable(targetEntityName, target.type())) {
            throw new StepSemanticException(
                    "Entity #" + owner.id() + " " + owner.type() + "." + attribute.name()
                            + " expected reference to " + targetEntityName + " but found " + target.type()
            );
        }
        return new StepEntityReference(target.id(), target.type());
    }

    private static List<Object> requireList(
            StepEntityInstance owner,
            StepAttributeDefinition attribute,
            StepValue value,
            Map<Integer, StepEntityInstance> entitiesById,
            StepSchemaDefinition schemaDefinition,
            StepTypeRef itemType
    ) {
        if (!(value instanceof StepListValue listValue)) {
            throw wrongType(owner, attribute, "LIST", value);
        }
        if (attribute.type().minSize() != null && listValue.values().size() < attribute.type().minSize()) {
            throw new StepSemanticException("Entity #" + owner.id() + " " + owner.type() + "." + attribute.name()
                    + " requires at least " + attribute.type().minSize() + " list items");
        }
        if (attribute.type().maxSize() != null && listValue.values().size() > attribute.type().maxSize()) {
            throw new StepSemanticException("Entity #" + owner.id() + " " + owner.type() + "." + attribute.name()
                    + " allows at most " + attribute.type().maxSize() + " list items");
        }
        List<Object> mapped = new ArrayList<>();
        StepAttributeDefinition nested = new StepAttributeDefinition(
                attribute.name(),
                itemType,
                StepCardinality.REQUIRED,
                StepAttributeKind.EXPLICIT
        );
        for (StepValue entry : listValue.values()) {
            mapped.add(mapValue(owner, nested, entry, entitiesById, schemaDefinition));
        }
        return List.copyOf(mapped);
    }

    private static Object requireSelect(
            StepEntityInstance owner,
            StepAttributeDefinition attribute,
            StepValue value,
            Map<Integer, StepEntityInstance> entitiesById,
            StepSchemaDefinition schemaDefinition,
            List<String> options
    ) {
        if (value instanceof StepStringValue && options.contains("STRING")) {
            return ((StepStringValue) value).value();
        }
        if (value instanceof StepNumberValue && options.contains("NUMBER")) {
            return ((StepNumberValue) value).value();
        }
        if (value instanceof StepEnumValue enumValue && options.contains("ENUM")) {
            return enumValue.value();
        }
        if (value instanceof StepReferenceValue referenceValue) {
            StepEntityInstance target = referenceValue.target();
            if (target == null) {
                target = entitiesById.get(referenceValue.referenceId());
            }
            String targetType = target == null ? null : target.type();
            if (target != null && options.stream().anyMatch(option -> schemaDefinition.isAssignable(option, targetType))) {
                return new StepEntityReference(target.id(), target.type());
            }
        }
        throw new StepSemanticException(
                "Entity #" + owner.id() + " " + owner.type() + "." + attribute.name()
                        + " does not match SELECT options " + options
        );
    }

    private static Object mapGenericValue(StepValue value, Map<Integer, StepEntityInstance> entitiesById) {
        return switch (value) {
            case StepStringValue stringValue -> stringValue.value();
            case StepNumberValue numberValue -> numberValue.value();
            case StepEnumValue enumValue -> enumValue.value();
            case StepReferenceValue referenceValue -> {
                StepEntityInstance target = referenceValue.target();
                if (target == null) {
                    target = entitiesById.get(referenceValue.referenceId());
                }
                yield target == null
                        ? new StepEntityReference(referenceValue.referenceId(), null)
                        : new StepEntityReference(target.id(), target.type());
            }
            case StepTypedValue typedValue -> java.util.Map.of(
                    "type", typedValue.type(),
                    "arguments", typedValue.arguments().stream()
                            .map(entry -> mapGenericValue(entry, entitiesById))
                            .toList()
            );
            case StepListValue listValue -> listValue.values().stream()
                    .map(entry -> mapGenericValue(entry, entitiesById))
                    .toList();
            case StepOmittedValue omittedValue -> null;
            case StepDerivedValue derivedValue -> "*";
        };
    }

    private static StepSemanticException wrongType(
            StepEntityInstance owner,
            StepAttributeDefinition attribute,
            String expected,
            StepValue actual
    ) {
        return new StepSemanticException(
                "Entity #" + owner.id() + " " + owner.type() + "." + attribute.name()
                        + " expected " + expected + " but found " + actual.getClass().getSimpleName()
        );
    }
}
