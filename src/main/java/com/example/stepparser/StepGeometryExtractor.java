package com.example.stepparser;

import com.example.stepparser.model.StepDataSection;
import com.example.stepparser.model.StepEntityInstance;
import com.example.stepparser.model.StepEnumValue;
import com.example.stepparser.model.StepListValue;
import com.example.stepparser.model.StepNumberValue;
import com.example.stepparser.model.StepReferenceValue;
import com.example.stepparser.model.StepValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class StepGeometryExtractor {

    private final Map<Path, GeometryFile> cache = new LinkedHashMap<>();

    StepGeometry extract(Path stepFile, StepAssemblyTree.ProductDefinitionInfo product) throws IOException {
        GeometryFile geometryFile = load(stepFile);
        return geometryFile.geometryFor(product);
    }

    private synchronized GeometryFile load(Path stepFile) throws IOException {
        Path normalized = stepFile.toAbsolutePath().normalize();
        GeometryFile cached = cache.get(normalized);
        if (cached != null) {
            return cached;
        }
        GeometryFile loaded = new GeometryFile(StepFileParser.parse(normalized).data());
        cache.put(normalized, loaded);
        return loaded;
    }

    private static final class GeometryFile {

        private final StepDataSection data;
        private final Map<Integer, StepEntityInstance> entitiesById;
        private final Map<Integer, Integer> shapeByDefinitionId;
        private final Map<Integer, Integer> representationByShapeId;
        private final Map<Integer, List<Integer>> linkedRepresentationIdsByRepresentationId;

        private GeometryFile(StepDataSection data) {
            this.data = data;
            this.entitiesById = new LinkedHashMap<>();
            this.shapeByDefinitionId = new LinkedHashMap<>();
            this.representationByShapeId = new LinkedHashMap<>();
            this.linkedRepresentationIdsByRepresentationId = new LinkedHashMap<>();

            for (StepEntityInstance entity : data.entities()) {
                entitiesById.put(entity.id(), entity);
            }
            indexDefinitions();
            indexRepresentations();
            indexRepresentationRelationships();
        }

        private StepGeometry geometryFor(StepAssemblyTree.ProductDefinitionInfo product) {
            String meshName = displayName(product);
            Integer shapeId = shapeByDefinitionId.get(product.definitionId());
            if (shapeId == null) {
                return placeholder(product, "No PRODUCT_DEFINITION_SHAPE was found for this product definition.");
            }

            Integer representationId = representationByShapeId.get(shapeId);
            if (representationId == null) {
                return placeholder(product, "No SHAPE_DEFINITION_REPRESENTATION was found for this product definition.");
            }

            Integer geometryRepresentationId = resolveGeometryRepresentationId(representationId);
            StepEntityInstance representation = entitiesById.get(geometryRepresentationId);
            if (representation == null || representation.parameters().size() < 2) {
                return placeholder(product, "The referenced SHAPE_REPRESENTATION is incomplete.");
            }

            List<StepEntityInstance> items = referencedEntities(representation.parameters().get(1));
            Transform transform = items.stream()
                    .filter(item -> "AXIS2_PLACEMENT_3D".equals(item.type()))
                    .findFirst()
                    .map(this::axisPlacementTransform)
                    .orElse(Transform.identity());
            List<StepEntityInstance> breps = items.stream()
                    .filter(item -> "MANIFOLD_SOLID_BREP".equals(item.type()))
                    .toList();
            if (breps.isEmpty()) {
                return placeholder(product, "Only MANIFOLD_SOLID_BREP items are approximated in the built-in Java exporter.");
            }

            List<Float> positions = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            for (StepEntityInstance brep : breps) {
                addBrepFaces(brep, transform, positions, indices);
            }
            if (indices.isEmpty()) {
                return placeholder(product, "No triangulatable face loops were found in the MANIFOLD_SOLID_BREP.");
            }

            return new StepGeometry(
                    meshName,
                    positions,
                    indices,
                    colorFor(product.definitionId()),
                    "Approximated Java GLB from MANIFOLD_SOLID_BREP face loops"
                            + (geometryRepresentationId.equals(representationId) ? "" : " via linked representation #" + geometryRepresentationId)
                            + ". Curved surfaces are triangulated crudely."
            );
        }

        private void addBrepFaces(
                StepEntityInstance brep,
                Transform transform,
                List<Float> positions,
                List<Integer> indices
        ) {
            if (brep.parameters().size() < 2) {
                return;
            }
            StepEntityInstance shell = referencedEntity(brep.parameters().get(1)).orElse(null);
            if (shell == null || shell.parameters().size() < 2) {
                return;
            }
            List<StepEntityInstance> faces = referencedEntities(shell.parameters().get(1)).stream()
                    .filter(entity -> "ADVANCED_FACE".equals(entity.type()))
                    .toList();
            for (StepEntityInstance face : faces) {
                addFace(face, transform, positions, indices);
            }
        }

        private void addFace(
                StepEntityInstance face,
                Transform transform,
                List<Float> positions,
                List<Integer> indices
        ) {
            if (face.parameters().size() < 2) {
                return;
            }
            List<StepEntityInstance> bounds = referencedEntities(face.parameters().get(1));
            StepEntityInstance outerBound = bounds.stream()
                    .filter(bound -> "FACE_OUTER_BOUND".equals(bound.type()) || "FACE_BOUND".equals(bound.type()))
                    .findFirst()
                    .orElse(null);
            if (outerBound == null || outerBound.parameters().size() < 2) {
                return;
            }

            StepEntityInstance loop = referencedEntity(outerBound.parameters().get(1)).orElse(null);
            if (loop == null || !"EDGE_LOOP".equals(loop.type()) || loop.parameters().size() < 2) {
                return;
            }

            List<float[]> vertices = edgeLoopVertices(loop);
            if (vertices.size() < 3) {
                return;
            }

            int baseVertex = positions.size() / 3;
            for (float[] vertex : vertices) {
                float[] transformed = transform.apply(vertex);
                positions.add(transformed[0]);
                positions.add(transformed[1]);
                positions.add(transformed[2]);
            }
            for (int[] triangle : triangulate(vertices)) {
                indices.add(baseVertex + triangle[0]);
                indices.add(baseVertex + triangle[1]);
                indices.add(baseVertex + triangle[2]);
            }
        }

        private List<float[]> edgeLoopVertices(StepEntityInstance loop) {
            List<StepEntityInstance> orientedEdges = referencedEntities(loop.parameters().get(1));
            List<float[]> vertices = new ArrayList<>();
            Set<String> dedup = new LinkedHashSet<>();
            for (StepEntityInstance orientedEdge : orientedEdges) {
                if (!"ORIENTED_EDGE".equals(orientedEdge.type()) || orientedEdge.parameters().size() < 5) {
                    continue;
                }
                StepEntityInstance edgeCurve = referencedEntity(orientedEdge.parameters().get(3)).orElse(null);
                if (edgeCurve == null || !"EDGE_CURVE".equals(edgeCurve.type()) || edgeCurve.parameters().size() < 3) {
                    continue;
                }
                boolean sameSense = logicalValue(orientedEdge.parameters().get(4));
                StepEntityInstance vertexPoint = referencedEntity(edgeCurve.parameters().get(sameSense ? 1 : 2)).orElse(null);
                float[] point = cartesianPoint(vertexPoint).orElse(null);
                if (point == null) {
                    continue;
                }
                String key = point[0] + ":" + point[1] + ":" + point[2];
                if (dedup.add(key)) {
                    vertices.add(point);
                }
            }
            return vertices;
        }

        private Optional<float[]> cartesianPoint(StepEntityInstance vertexPoint) {
            if (vertexPoint == null || !"VERTEX_POINT".equals(vertexPoint.type()) || vertexPoint.parameters().size() < 2) {
                return Optional.empty();
            }
            StepEntityInstance point = referencedEntity(vertexPoint.parameters().get(1)).orElse(null);
            if (point == null || !"CARTESIAN_POINT".equals(point.type()) || point.parameters().size() < 2) {
                return Optional.empty();
            }
            if (!(point.parameters().get(1) instanceof StepListValue coordinates) || coordinates.values().size() < 3) {
                return Optional.empty();
            }
            Float x = number(coordinates.values().get(0));
            Float y = number(coordinates.values().get(1));
            Float z = number(coordinates.values().get(2));
            if (x == null || y == null || z == null) {
                return Optional.empty();
            }
            return Optional.of(new float[]{x, y, z});
        }

        private List<StepEntityInstance> referencedEntities(StepValue value) {
            if (!(value instanceof StepListValue listValue)) {
                StepEntityInstance single = referencedEntity(value).orElse(null);
                return single == null ? List.of() : List.of(single);
            }
            List<StepEntityInstance> resolved = new ArrayList<>();
            for (StepValue entry : listValue.values()) {
                referencedEntity(entry).ifPresent(resolved::add);
            }
            return resolved;
        }

        private Optional<StepEntityInstance> referencedEntity(StepValue value) {
            if (!(value instanceof StepReferenceValue referenceValue)) {
                return Optional.empty();
            }
            StepEntityInstance target = referenceValue.target();
            if (target != null) {
                return Optional.of(target);
            }
            return Optional.ofNullable(entitiesById.get(referenceValue.referenceId()));
        }

        private void indexDefinitions() {
            for (StepEntityInstance entity : data.entities()) {
                if (!"PRODUCT_DEFINITION_SHAPE".equals(entity.type()) || entity.parameters().size() < 3) {
                    continue;
                }
                referencedEntity(entity.parameters().get(2))
                        .filter(definition -> "PRODUCT_DEFINITION".equals(definition.type()))
                        .ifPresent(definition -> shapeByDefinitionId.put(definition.id(), entity.id()));
            }
        }

        private void indexRepresentations() {
            for (StepEntityInstance entity : data.entities()) {
                if (!"SHAPE_DEFINITION_REPRESENTATION".equals(entity.type()) || entity.parameters().size() < 2) {
                    continue;
                }
                Integer shapeId = referencedEntity(entity.parameters().get(0)).map(StepEntityInstance::id).orElse(null);
                Integer representationId = referencedEntity(entity.parameters().get(1)).map(StepEntityInstance::id).orElse(null);
                if (shapeId != null && representationId != null) {
                    representationByShapeId.put(shapeId, representationId);
                }
            }
        }

        private void indexRepresentationRelationships() {
            for (StepEntityInstance entity : data.entities()) {
                if (!isRepresentationRelationship(entity) || entity.parameters().size() < 4) {
                    continue;
                }
                Integer firstRepresentationId = referencedEntity(entity.parameters().get(2)).map(StepEntityInstance::id).orElse(null);
                Integer secondRepresentationId = referencedEntity(entity.parameters().get(3)).map(StepEntityInstance::id).orElse(null);
                if (firstRepresentationId == null || secondRepresentationId == null) {
                    continue;
                }
                linkedRepresentationIdsByRepresentationId
                        .computeIfAbsent(firstRepresentationId, ignored -> new ArrayList<>())
                        .add(secondRepresentationId);
                linkedRepresentationIdsByRepresentationId
                        .computeIfAbsent(secondRepresentationId, ignored -> new ArrayList<>())
                        .add(firstRepresentationId);
            }
        }

        private Integer resolveGeometryRepresentationId(Integer representationId) {
            List<Integer> queue = new ArrayList<>();
            Set<Integer> visited = new LinkedHashSet<>();
            queue.add(representationId);

            for (int index = 0; index < queue.size(); index++) {
                Integer currentId = queue.get(index);
                if (!visited.add(currentId)) {
                    continue;
                }
                StepEntityInstance representation = entitiesById.get(currentId);
                if (representation != null && representation.parameters().size() >= 2) {
                    List<StepEntityInstance> items = referencedEntities(representation.parameters().get(1));
                    if (items.stream().anyMatch(item -> "MANIFOLD_SOLID_BREP".equals(item.type()))) {
                        return currentId;
                    }
                }
                for (Integer linkedId : linkedRepresentationIdsByRepresentationId.getOrDefault(currentId, List.of())) {
                    if (!visited.contains(linkedId)) {
                        queue.add(linkedId);
                    }
                }
            }
            return representationId;
        }

        private boolean isRepresentationRelationship(StepEntityInstance entity) {
            return "SHAPE_REPRESENTATION_RELATIONSHIP".equals(entity.type())
                    || "REPRESENTATION_RELATIONSHIP".equals(entity.type());
        }

        private static boolean logicalValue(StepValue value) {
            return value instanceof StepEnumValue enumValue && "T".equals(enumValue.value());
        }

        private static Float number(StepValue value) {
            if (value instanceof StepNumberValue numberValue) {
                return numberValue.value().floatValue();
            }
            return null;
        }

        private static StepGeometry placeholder(StepAssemblyTree.ProductDefinitionInfo product, String reason) {
            return new StepGeometry(
                    displayName(product),
                    List.of(),
                    List.of(),
                    colorFor(product.definitionId()),
                    "Falling back to placeholder geometry. " + reason
            );
        }

        private static String displayName(StepAssemblyTree.ProductDefinitionInfo product) {
            if (product.name() != null && !product.name().isBlank()) {
                return product.name();
            }
            if (product.productId() != null && !product.productId().isBlank()) {
                return product.productId();
            }
            return "definition-" + product.definitionId();
        }

        private static float[] colorFor(int definitionId) {
            return new float[]{
                    0.35f + ((definitionId * 37) % 50) / 100.0f,
                    0.40f + ((definitionId * 17) % 35) / 100.0f,
                    0.45f + ((definitionId * 23) % 30) / 100.0f
            };
        }

        private Transform axisPlacementTransform(StepEntityInstance placement) {
            if (placement.parameters().size() < 4) {
                return Transform.identity();
            }
            float[] origin = referencedEntity(placement.parameters().get(1))
                    .flatMap(this::cartesianPointFromEntity)
                    .orElse(new float[]{0.0f, 0.0f, 0.0f});
            float[] zAxis = referencedEntity(placement.parameters().get(2))
                    .flatMap(this::direction)
                    .orElse(new float[]{0.0f, 0.0f, 1.0f});
            float[] xAxis = referencedEntity(placement.parameters().get(3))
                    .flatMap(this::direction)
                    .orElse(new float[]{1.0f, 0.0f, 0.0f});

            float[] z = normalize(zAxis, new float[]{0.0f, 0.0f, 1.0f});
            float dot = dot(xAxis, z);
            float[] xProjected = new float[]{
                    xAxis[0] - z[0] * dot,
                    xAxis[1] - z[1] * dot,
                    xAxis[2] - z[2] * dot
            };
            float[] x = normalize(xProjected, fallbackXAxis(z));
            float[] y = normalize(cross(z, x), new float[]{0.0f, 1.0f, 0.0f});
            return new Transform(origin, x, y, z);
        }

        private Optional<float[]> direction(StepEntityInstance entity) {
            if (entity == null || !"DIRECTION".equals(entity.type()) || entity.parameters().size() < 2) {
                return Optional.empty();
            }
            if (!(entity.parameters().get(1) instanceof StepListValue coordinates) || coordinates.values().size() < 3) {
                return Optional.empty();
            }
            Float x = number(coordinates.values().get(0));
            Float y = number(coordinates.values().get(1));
            Float z = number(coordinates.values().get(2));
            if (x == null || y == null || z == null) {
                return Optional.empty();
            }
            return Optional.of(new float[]{x, y, z});
        }

        private Optional<float[]> cartesianPointFromEntity(StepEntityInstance point) {
            if (point == null || !"CARTESIAN_POINT".equals(point.type()) || point.parameters().size() < 2) {
                return Optional.empty();
            }
            if (!(point.parameters().get(1) instanceof StepListValue coordinates) || coordinates.values().size() < 3) {
                return Optional.empty();
            }
            Float x = number(coordinates.values().get(0));
            Float y = number(coordinates.values().get(1));
            Float z = number(coordinates.values().get(2));
            if (x == null || y == null || z == null) {
                return Optional.empty();
            }
            return Optional.of(new float[]{x, y, z});
        }

        private static float[] normalize(float[] vector, float[] fallback) {
            float length = (float) Math.sqrt(dot(vector, vector));
            if (length == 0.0f) {
                return fallback;
            }
            return new float[]{vector[0] / length, vector[1] / length, vector[2] / length};
        }

        private static float dot(float[] left, float[] right) {
            return left[0] * right[0] + left[1] * right[1] + left[2] * right[2];
        }

        private static float[] cross(float[] left, float[] right) {
            return new float[]{
                    left[1] * right[2] - left[2] * right[1],
                    left[2] * right[0] - left[0] * right[2],
                    left[0] * right[1] - left[1] * right[0]
            };
        }

        private static float[] fallbackXAxis(float[] zAxis) {
            if (Math.abs(zAxis[0]) < 0.9f) {
                return new float[]{1.0f, 0.0f, 0.0f};
            }
            return new float[]{0.0f, 1.0f, 0.0f};
        }

        private List<int[]> triangulate(List<float[]> vertices) {
            if (vertices.size() < 3) {
                return List.of();
            }

            float[] normal = polygonNormal(vertices);
            int projectionAxis = dominantAxis(normal);
            List<float[]> projected = project(vertices, projectionAxis);
            float signedArea = signedArea(projected);

            List<Integer> remaining = new ArrayList<>();
            for (int index = 0; index < vertices.size(); index++) {
                remaining.add(index);
            }
            if (signedArea < 0.0f) {
                Collections.reverse(remaining);
            }

            List<int[]> triangles = new ArrayList<>();
            int guard = 0;
            while (remaining.size() > 3 && guard < vertices.size() * vertices.size()) {
                boolean clipped = false;
                for (int index = 0; index < remaining.size(); index++) {
                    int previous = remaining.get((index - 1 + remaining.size()) % remaining.size());
                    int current = remaining.get(index);
                    int next = remaining.get((index + 1) % remaining.size());
                    if (!isEar(previous, current, next, remaining, projected)) {
                        continue;
                    }
                    triangles.add(new int[]{previous, current, next});
                    remaining.remove(index);
                    clipped = true;
                    break;
                }
                if (!clipped) {
                    return fallbackFan(vertices.size());
                }
                guard++;
            }

            if (remaining.size() == 3) {
                triangles.add(new int[]{remaining.get(0), remaining.get(1), remaining.get(2)});
            }
            return triangles;
        }

        private static List<int[]> fallbackFan(int vertexCount) {
            List<int[]> triangles = new ArrayList<>();
            for (int index = 1; index < vertexCount - 1; index++) {
                triangles.add(new int[]{0, index, index + 1});
            }
            return triangles;
        }

        private static boolean isEar(
                int previous,
                int current,
                int next,
                List<Integer> polygon,
                List<float[]> projected
        ) {
            float[] a = projected.get(previous);
            float[] b = projected.get(current);
            float[] c = projected.get(next);
            if (cross2d(a, b, c) <= 1.0e-6f) {
                return false;
            }

            for (int candidate : polygon) {
                if (candidate == previous || candidate == current || candidate == next) {
                    continue;
                }
                if (pointInTriangle(projected.get(candidate), a, b, c)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean pointInTriangle(float[] point, float[] a, float[] b, float[] c) {
            float area1 = cross2d(point, a, b);
            float area2 = cross2d(point, b, c);
            float area3 = cross2d(point, c, a);
            boolean hasNegative = area1 < -1.0e-6f || area2 < -1.0e-6f || area3 < -1.0e-6f;
            boolean hasPositive = area1 > 1.0e-6f || area2 > 1.0e-6f || area3 > 1.0e-6f;
            return !(hasNegative && hasPositive);
        }

        private static float cross2d(float[] a, float[] b, float[] c) {
            float abX = b[0] - a[0];
            float abY = b[1] - a[1];
            float acX = c[0] - a[0];
            float acY = c[1] - a[1];
            return abX * acY - abY * acX;
        }

        private static float[] polygonNormal(List<float[]> vertices) {
            float nx = 0.0f;
            float ny = 0.0f;
            float nz = 0.0f;
            for (int index = 0; index < vertices.size(); index++) {
                float[] current = vertices.get(index);
                float[] next = vertices.get((index + 1) % vertices.size());
                nx += (current[1] - next[1]) * (current[2] + next[2]);
                ny += (current[2] - next[2]) * (current[0] + next[0]);
                nz += (current[0] - next[0]) * (current[1] + next[1]);
            }
            return new float[]{nx, ny, nz};
        }

        private static int dominantAxis(float[] normal) {
            float x = Math.abs(normal[0]);
            float y = Math.abs(normal[1]);
            float z = Math.abs(normal[2]);
            if (x >= y && x >= z) {
                return 0;
            }
            if (y >= z) {
                return 1;
            }
            return 2;
        }

        private static List<float[]> project(List<float[]> vertices, int axis) {
            List<float[]> projected = new ArrayList<>(vertices.size());
            for (float[] vertex : vertices) {
                projected.add(switch (axis) {
                    case 0 -> new float[]{vertex[1], vertex[2]};
                    case 1 -> new float[]{vertex[0], vertex[2]};
                    default -> new float[]{vertex[0], vertex[1]};
                });
            }
            return projected;
        }

        private static float signedArea(List<float[]> vertices) {
            float area = 0.0f;
            for (int index = 0; index < vertices.size(); index++) {
                float[] current = vertices.get(index);
                float[] next = vertices.get((index + 1) % vertices.size());
                area += current[0] * next[1] - next[0] * current[1];
            }
            return area * 0.5f;
        }
    }

    private record Transform(
            float[] origin,
            float[] xAxis,
            float[] yAxis,
            float[] zAxis
    ) {

        private static Transform identity() {
            return new Transform(
                    new float[]{0.0f, 0.0f, 0.0f},
                    new float[]{1.0f, 0.0f, 0.0f},
                    new float[]{0.0f, 1.0f, 0.0f},
                    new float[]{0.0f, 0.0f, 1.0f}
            );
        }

        private float[] apply(float[] point) {
            return new float[]{
                    origin[0] + xAxis[0] * point[0] + yAxis[0] * point[1] + zAxis[0] * point[2],
                    origin[1] + xAxis[1] * point[0] + yAxis[1] * point[1] + zAxis[1] * point[2],
                    origin[2] + xAxis[2] * point[0] + yAxis[2] * point[1] + zAxis[2] * point[2]
            };
        }
    }
}
