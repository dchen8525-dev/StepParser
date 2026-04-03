package com.example.stepparser.model;

import java.util.List;

public enum StepSchemas {
    AP203,
    AP214,
    AP242,
    UNKNOWN;

    public static StepSchemas detect(List<String> schemaNames) {
        for (String schemaName : schemaNames) {
            String normalized = schemaName.toUpperCase();
            if (normalized.contains("CONFIG_CONTROL_DESIGN")) {
                return AP203;
            }
            if (normalized.contains("AUTOMOTIVE_DESIGN")) {
                return AP214;
            }
            if (normalized.contains("AP242")) {
                return AP242;
            }
        }
        return UNKNOWN;
    }
}
