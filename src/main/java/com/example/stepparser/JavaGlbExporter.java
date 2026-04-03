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
        byte[] glb = SimpleGlbWriter.writeGeometry(geometry);
        Files.write(request.outputFile(), glb);
        return ExportResult.success();
    }

    @Override
    public String unavailableReason() {
        return WARNING;
    }
}
