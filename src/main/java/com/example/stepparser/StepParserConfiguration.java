package com.example.stepparser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class StepParserConfiguration {

    static final String GLB_EXPORT_ENV = "STEP_PARSER_GLB_EXPORT_COMMAND";
    static final String GLB_EXPORT_PROPERTY = "step.parser.glb-export-command";
    static final String CONFIG_FILE_NAME = ".step-parser.properties";

    private StepParserConfiguration() {
    }

    static String resolveGlbExportCommand(Path workingDirectory) throws IOException {
        String environmentValue = readConfiguredValue(System.getenv(GLB_EXPORT_ENV));
        if (environmentValue != null) {
            return environmentValue;
        }

        String propertyValue = readConfiguredValue(System.getProperty(GLB_EXPORT_PROPERTY));
        if (propertyValue != null) {
            return propertyValue;
        }

        if (workingDirectory == null) {
            return null;
        }

        Path configFile = workingDirectory.resolve(CONFIG_FILE_NAME);
        if (!Files.isRegularFile(configFile)) {
            return null;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile)) {
            properties.load(reader);
        }
        return readConfiguredValue(properties.getProperty(GLB_EXPORT_PROPERTY));
    }

    static String missingGlbExporterMessage() {
        return "GLB exporter is not configured. Set STEP_PARSER_GLB_EXPORT_COMMAND, "
                + "or define step.parser.glb-export-command in a JVM property or .step-parser.properties.";
    }

    private static String readConfiguredValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
