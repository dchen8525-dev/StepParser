package com.example.stepparser;

import java.io.IOException;
import java.nio.file.Path;

public interface StepGlbExporter {

    ExportResult export(ExportRequest request) throws IOException;

    static StepGlbExporter disabled(String reason) {
        return request -> ExportResult.failure(reason);
    }

    record ExportRequest(
            Path stepFile,
            StepAssemblyTree.ProductDefinitionInfo product,
            Path outputFile
    ) {
    }

    record ExportResult(
            boolean exported,
            String error
    ) {

        public static ExportResult success() {
            return new ExportResult(true, null);
        }

        public static ExportResult failure(String error) {
            return new ExportResult(false, error);
        }
    }
}
