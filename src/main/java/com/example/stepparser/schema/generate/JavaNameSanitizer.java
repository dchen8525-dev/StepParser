package com.example.stepparser.schema.generate;

final class JavaNameSanitizer {

    private JavaNameSanitizer() {
    }

    static String toTypeName(String stepName) {
        StringBuilder builder = new StringBuilder();
        for (String part : stepName.toLowerCase().split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    static String toFieldName(String stepName) {
        String typeName = toTypeName(stepName);
        return Character.toLowerCase(typeName.charAt(0)) + typeName.substring(1);
    }
}
