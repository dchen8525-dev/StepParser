package com.example.stepparser;

import com.example.stepparser.model.StepDataSection;
import com.example.stepparser.model.StepEntityInstance;
import com.example.stepparser.model.StepReferenceValue;
import com.example.stepparser.model.StepStringValue;
import com.example.stepparser.model.StepValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class StepAssemblyTree {

    private static final String NEXT_ASSEMBLY_USAGE_OCCURRENCE = "NEXT_ASSEMBLY_USAGE_OCCURRENCE";
    private static final String PRODUCT_DEFINITION = "PRODUCT_DEFINITION";
    private static final String PRODUCT = "PRODUCT";

    private StepAssemblyTree() {
    }

    public static List<AssemblyNode> roots(StepDataSection dataSection) {
        Map<Integer, StepEntityInstance> entitiesById = new LinkedHashMap<>();
        for (StepEntityInstance entity : dataSection.entities()) {
            entitiesById.put(entity.id(), entity);
        }

        Map<Integer, ProductDefinitionInfo> definitions = new LinkedHashMap<>();
        List<AssemblyLink> links = new ArrayList<>();

        for (StepEntityInstance entity : dataSection.entities()) {
            switch (entity.type()) {
                case PRODUCT_DEFINITION -> resolveProductDefinition(entity, entitiesById)
                        .ifPresent(info -> definitions.put(info.definitionId(), info));
                case NEXT_ASSEMBLY_USAGE_OCCURRENCE -> resolveAssemblyLink(entity).ifPresent(links::add);
                default -> {
                }
            }
        }

        if (links.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<AssemblyLink>> linksByParent = new LinkedHashMap<>();
        Set<Integer> childDefinitionIds = new LinkedHashSet<>();
        for (AssemblyLink link : links) {
            linksByParent.computeIfAbsent(link.parentDefinitionId(), ignored -> new ArrayList<>()).add(link);
            childDefinitionIds.add(link.childDefinitionId());
        }

        List<Integer> rootDefinitionIds = new ArrayList<>();
        for (AssemblyLink link : links) {
            if (!childDefinitionIds.contains(link.parentDefinitionId()) && !rootDefinitionIds.contains(link.parentDefinitionId())) {
                rootDefinitionIds.add(link.parentDefinitionId());
            }
        }
        if (rootDefinitionIds.isEmpty()) {
            for (Integer parentDefinitionId : linksByParent.keySet()) {
                if (!rootDefinitionIds.contains(parentDefinitionId)) {
                    rootDefinitionIds.add(parentDefinitionId);
                }
            }
        }

        List<AssemblyNode> roots = new ArrayList<>();
        for (Integer rootDefinitionId : rootDefinitionIds) {
            roots.add(buildNode(rootDefinitionId, definitions, linksByParent, new LinkedHashSet<>()));
        }
        return List.copyOf(roots);
    }

    public static String format(StepDataSection dataSection) {
        List<AssemblyNode> roots = roots(dataSection);
        if (roots.isEmpty()) {
            return "No assembly relationships found.";
        }

        List<String> lines = new ArrayList<>();
        lines.add("Assembly tree:");
        for (int index = 0; index < roots.size(); index++) {
            appendNode(lines, roots.get(index), "", index == roots.size() - 1, new LinkedHashSet<>());
        }
        return String.join(System.lineSeparator(), lines);
    }

    private static AssemblyNode buildNode(
            int definitionId,
            Map<Integer, ProductDefinitionInfo> definitions,
            Map<Integer, List<AssemblyLink>> linksByParent,
            Set<Integer> path
    ) {
        ProductDefinitionInfo info = definitions.getOrDefault(definitionId, ProductDefinitionInfo.missing(definitionId));
        if (!path.add(definitionId)) {
            return new AssemblyNode(info, List.of());
        }
        List<AssemblyOccurrence> children = new ArrayList<>();
        for (AssemblyLink link : linksByParent.getOrDefault(definitionId, List.of())) {
            children.add(new AssemblyOccurrence(
                    link.occurrenceEntityId(),
                    link.occurrenceId(),
                    buildNode(link.childDefinitionId(), definitions, linksByParent, path)
            ));
        }
        path.remove(definitionId);
        return new AssemblyNode(info, List.copyOf(children));
    }

    private static void appendNode(
            List<String> lines,
            AssemblyNode node,
            String indent,
            boolean last,
            Set<Integer> path
    ) {
        String branch = indent.isEmpty() ? "" : (last ? "\\- " : "+- ");
        lines.add(indent + branch + describe(node.product()));

        if (!path.add(node.product().definitionId())) {
            lines.add(indent + (last ? "   " : "|  ") + "[cycle detected]");
            return;
        }

        String childIndent = indent + (indent.isEmpty() ? "" : (last ? "   " : "|  "));
        List<AssemblyOccurrence> children = node.children();
        for (int index = 0; index < children.size(); index++) {
            AssemblyOccurrence child = children.get(index);
            boolean childLast = index == children.size() - 1;
            lines.add(childIndent + (childLast ? "\\- " : "+- ") + occurrenceLabel(child));
            appendChildNode(lines, child.node(), childIndent, childLast, path);
        }
        path.remove(node.product().definitionId());
    }

    private static void appendChildNode(
            List<String> lines,
            AssemblyNode node,
            String indent,
            boolean last,
            Set<Integer> path
    ) {
        String nodeIndent = indent + (last ? "   " : "|  ");
        lines.add(nodeIndent + describe(node.product()));

        if (!path.add(node.product().definitionId())) {
            lines.add(nodeIndent + (last ? "   " : "|  ") + "[cycle detected]");
            return;
        }

        List<AssemblyOccurrence> children = node.children();
        for (int index = 0; index < children.size(); index++) {
            AssemblyOccurrence child = children.get(index);
            boolean childLast = index == children.size() - 1;
            lines.add(nodeIndent + (childLast ? "\\- " : "+- ") + occurrenceLabel(child));
            appendChildNode(lines, child.node(), nodeIndent, childLast, path);
        }
        path.remove(node.product().definitionId());
    }

    private static String occurrenceLabel(AssemblyOccurrence occurrence) {
        StringBuilder builder = new StringBuilder();
        builder.append("occurrence ");
        String occurrenceId = occurrence.occurrenceId();
        if (occurrenceId == null || occurrenceId.isBlank()) {
            builder.append('#').append(occurrence.occurrenceEntityId());
        } else {
            builder.append(occurrenceId);
        }
        builder.append(" (#").append(occurrence.occurrenceEntityId()).append(')');
        return builder.toString();
    }

    private static String describe(ProductDefinitionInfo info) {
        StringBuilder builder = new StringBuilder();
        builder.append("#").append(info.definitionId()).append(' ');
        String name = firstNonBlank(info.name(), info.productId(), "<unnamed>");
        builder.append(name);
        if (info.productId() != null && !info.productId().isBlank() && !info.productId().equals(name)) {
            builder.append(" [").append(info.productId()).append(']');
        }
        if (info.productEntityId() != null) {
            builder.append(" {product #").append(info.productEntityId()).append('}');
        }
        return builder.toString();
    }

    private static Optional<ProductDefinitionInfo> resolveProductDefinition(
            StepEntityInstance entity,
            Map<Integer, StepEntityInstance> entitiesById
    ) {
        if (entity.parameters().size() < 3) {
            return Optional.empty();
        }

        StepEntityInstance formation = referencedEntity(entitiesById, entity.parameters().get(2)).orElse(null);
        if (formation == null || formation.parameters().size() < 3) {
            return Optional.of(ProductDefinitionInfo.missing(entity.id()));
        }

        StepEntityInstance product = referencedEntity(entitiesById, formation.parameters().get(2)).orElse(null);
        if (product == null || !PRODUCT.equals(product.type())) {
            return Optional.of(ProductDefinitionInfo.missing(entity.id()));
        }

        return Optional.of(new ProductDefinitionInfo(
                entity.id(),
                formation.id(),
                product.id(),
                stringValue(product.parameters(), 0),
                stringValue(product.parameters(), 1),
                stringValue(product.parameters(), 2)
        ));
    }

    private static Optional<AssemblyLink> resolveAssemblyLink(StepEntityInstance entity) {
        if (entity.parameters().size() < 5) {
            return Optional.empty();
        }

        Integer parentDefinitionId = referenceId(entity.parameters().get(3));
        Integer childDefinitionId = referenceId(entity.parameters().get(4));
        if (parentDefinitionId == null || childDefinitionId == null) {
            return Optional.empty();
        }

        return Optional.of(new AssemblyLink(
                entity.id(),
                stringValue(entity.parameters(), 0),
                parentDefinitionId,
                childDefinitionId
        ));
    }

    private static String stringValue(List<StepValue> parameters, int index) {
        if (index >= parameters.size()) {
            return null;
        }
        StepValue value = parameters.get(index);
        if (value instanceof StepStringValue stringValue) {
            return stringValue.value();
        }
        return null;
    }

    private static Integer referenceId(StepValue value) {
        if (value instanceof StepReferenceValue referenceValue) {
            return referenceValue.referenceId();
        }
        return null;
    }

    private static Optional<StepEntityInstance> referencedEntity(
            Map<Integer, StepEntityInstance> entitiesById,
            StepValue value
    ) {
        Integer referenceId = referenceId(value);
        if (referenceId != null) {
            return Optional.ofNullable(entitiesById.get(referenceId));
        }
        return Optional.empty();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public record AssemblyNode(ProductDefinitionInfo product, List<AssemblyOccurrence> children) {

        public AssemblyNode {
            children = List.copyOf(children);
        }
    }

    public record AssemblyOccurrence(int occurrenceEntityId, String occurrenceId, AssemblyNode node) {
    }

    public record ProductDefinitionInfo(
            int definitionId,
            Integer formationEntityId,
            Integer productEntityId,
            String productId,
            String name,
            String description
    ) {

        private static ProductDefinitionInfo missing(int definitionId) {
            return new ProductDefinitionInfo(definitionId, null, null, null, null, null);
        }
    }

    private record AssemblyLink(
            int occurrenceEntityId,
            String occurrenceId,
            int parentDefinitionId,
            int childDefinitionId
    ) {
    }
}
