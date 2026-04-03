package com.example.stepparser;

import com.example.stepparser.model.StepFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StepAssemblySceneBuilder {

    private StepAssemblySceneBuilder() {
    }

    public static StepAssemblyScene build(Path stepFile, Path assetDirectory, String assetBasePath) throws IOException {
        return build(stepFile, assetDirectory, assetBasePath, StepGlbExporter.disabled(
                "GLB exporter is not configured. Set STEP_PARSER_GLB_EXPORT_COMMAND."
        ));
    }

    public static StepAssemblyScene build(
            Path stepFile,
            Path assetDirectory,
            String assetBasePath,
            StepGlbExporter exporter
    ) throws IOException {
        Path normalizedStepFile = stepFile.toAbsolutePath().normalize();
        StepFile parsed = StepFileParser.parse(normalizedStepFile);
        List<StepAssemblyTree.AssemblyNode> roots = StepAssemblyTree.roots(parsed.data());
        List<String> warnings = new ArrayList<>();

        if (roots.isEmpty()) {
            warnings.add("No NEXT_ASSEMBLY_USAGE_OCCURRENCE relationships were found.");
        }

        Files.createDirectories(assetDirectory);
        Map<Integer, StepAssemblyScene.GlbAsset> assetsByDefinitionId = new LinkedHashMap<>();
        List<StepAssemblyScene.SceneNode> sceneRoots = new ArrayList<>();
        for (StepAssemblyTree.AssemblyNode root : roots) {
            sceneRoots.add(buildNode(
                    normalizedStepFile,
                    assetDirectory,
                    normalizeAssetBasePath(assetBasePath),
                    exporter,
                    assetsByDefinitionId,
                    warnings,
                    root,
                    "def-" + root.product().definitionId(),
                    null,
                    null,
                    new LinkedHashSet<>()
            ));
        }

        return new StepAssemblyScene(
                normalizedStepFile.toString(),
                parsed.header().schemaNames(),
                sceneRoots,
                warnings
        );
    }

    private static StepAssemblyScene.SceneNode buildNode(
            Path stepFile,
            Path assetDirectory,
            String assetBasePath,
            StepGlbExporter exporter,
            Map<Integer, StepAssemblyScene.GlbAsset> assetsByDefinitionId,
            List<String> warnings,
            StepAssemblyTree.AssemblyNode node,
            String instanceId,
            Integer occurrenceEntityId,
            String occurrenceId,
            Set<Integer> path
    ) throws IOException {
        StepAssemblyTree.ProductDefinitionInfo product = node.product();
        StepAssemblyScene.GlbAsset asset = assetFor(stepFile, assetDirectory, assetBasePath, exporter, assetsByDefinitionId, warnings, product);
        if (!path.add(product.definitionId())) {
            warnings.add("Cycle detected at product definition #" + product.definitionId() + ".");
            return toSceneNode(instanceId, occurrenceEntityId, occurrenceId, product, asset, List.of());
        }

        List<StepAssemblyScene.SceneNode> children = new ArrayList<>();
        for (StepAssemblyTree.AssemblyOccurrence child : node.children()) {
            String childInstanceId = instanceId + "/occ-" + child.occurrenceEntityId() + "/def-" + child.node().product().definitionId();
            children.add(buildNode(
                    stepFile,
                    assetDirectory,
                    assetBasePath,
                    exporter,
                    assetsByDefinitionId,
                    warnings,
                    child.node(),
                    childInstanceId,
                    child.occurrenceEntityId(),
                    child.occurrenceId(),
                    path
            ));
        }
        path.remove(product.definitionId());

        return toSceneNode(instanceId, occurrenceEntityId, occurrenceId, product, asset, children);
    }

    private static StepAssemblyScene.SceneNode toSceneNode(
            String instanceId,
            Integer occurrenceEntityId,
            String occurrenceId,
            StepAssemblyTree.ProductDefinitionInfo product,
            StepAssemblyScene.GlbAsset asset,
            List<StepAssemblyScene.SceneNode> children
    ) {
        return new StepAssemblyScene.SceneNode(
                instanceId,
                occurrenceEntityId,
                occurrenceId,
                product.definitionId(),
                product.formationEntityId(),
                product.productEntityId(),
                product.productId(),
                product.name(),
                product.description(),
                firstNonBlank(product.name(), product.productId(), "definition-" + product.definitionId()),
                asset,
                children
        );
    }

    private static StepAssemblyScene.GlbAsset assetFor(
            Path stepFile,
            Path assetDirectory,
            String assetBasePath,
            StepGlbExporter exporter,
            Map<Integer, StepAssemblyScene.GlbAsset> assetsByDefinitionId,
            List<String> warnings,
            StepAssemblyTree.ProductDefinitionInfo product
    ) throws IOException {
        StepAssemblyScene.GlbAsset cached = assetsByDefinitionId.get(product.definitionId());
        if (cached != null) {
            return cached;
        }

        String fileName = sanitizeFileName(product) + ".glb";
        Path outputFile = assetDirectory.resolve(fileName);
        StepGlbExporter.ExportResult result = exporter.export(new StepGlbExporter.ExportRequest(stepFile, product, outputFile));

        StepAssemblyScene.GlbAsset asset = new StepAssemblyScene.GlbAsset(
                fileName,
                result.exported() ? assetBasePath + "/" + fileName : null,
                result.exported(),
                result.error()
        );
        if (!result.exported() && result.error() != null && !result.error().isBlank()) {
            warnings.add("GLB export failed for definition #" + product.definitionId() + ": " + result.error());
        }

        assetsByDefinitionId.put(product.definitionId(), asset);
        return asset;
    }

    private static String sanitizeFileName(StepAssemblyTree.ProductDefinitionInfo product) {
        String base = firstNonBlank(product.name(), product.productId(), "definition");
        String sanitized = base.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-{2,}", "-");
        sanitized = sanitized.replaceAll("^-+", "").replaceAll("-+$", "");
        if (sanitized.isBlank()) {
            sanitized = "definition";
        }
        return sanitized + "-" + product.definitionId();
    }

    private static String normalizeAssetBasePath(String assetBasePath) {
        if (assetBasePath == null || assetBasePath.isBlank()) {
            return "/assets";
        }
        String trimmed = assetBasePath.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
