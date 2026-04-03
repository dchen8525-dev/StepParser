package com.example.stepparser;

import java.io.IOException;
import java.nio.file.Path;

public interface StepGlbExporter {

    ExportResult export(ExportRequest request) throws IOException;

    default String unavailableReason() {
        return null;
    }

    static StepGlbExporter disabled(String reason) {
        return new StepGlbExporter() {
            @Override
            public ExportResult export(ExportRequest request) {
                return ExportResult.failure(reason);
            }

            @Override
            public String unavailableReason() {
                return reason;
            }
        };
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
