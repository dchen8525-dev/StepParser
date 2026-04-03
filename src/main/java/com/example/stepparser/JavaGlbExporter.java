package com.example.stepparser;

import java.io.IOException;
import java.nio.file.Files;

public final class JavaGlbExporter implements StepGlbExporter {

    private static final String WARNING =
            "Using built-in Java GLB exporter. It approximates MANIFOLD_SOLID_BREP face loops and falls back to placeholders.";

    private final StepGeometryExtractor geometryExtractor = new StepGeometryExtractor();

    @Override
    public ExportResult export(ExportRequest request) throws IOException {
        Files.createDirectories(request.outputFile().getParent());
        StepGeometry geometry = geometryExtractor.extract(request.stepFile(), request.product());
        boolean placeholder = !geometry.hasTriangles();
        log(String.format(
                "Exporting definition #%d (%s) from %s -> %s | placeholder=%s | vertices=%d | triangles=%d | note=%s",
                request.product().definitionId(),
                displayName(request.product()),
                request.stepFile().toAbsolutePath().normalize(),
                request.outputFile().toAbsolutePath().normalize(),
                placeholder,
                geometry.positions().size() / 3,
                geometry.indices().size() / 3,
                geometry.warning()
        ));
        byte[] glb = SimpleGlbWriter.writeGeometry(geometry);
        Files.write(request.outputFile(), glb);
        return ExportResult.success();
    }

    @Override
    public String unavailableReason() {
        return WARNING;
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

    private static void log(String message) {
        System.out.println("[StepGlbExporter] " + message);
    }
}
