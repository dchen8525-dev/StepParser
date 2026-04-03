package com.example.stepparser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CommandLineGlbExporter implements StepGlbExporter {

    private final String commandTemplate;

    public CommandLineGlbExporter(String commandTemplate) {
        this.commandTemplate = commandTemplate;
    }

    @Override
    public ExportResult export(ExportRequest request) throws IOException {
        Files.createDirectories(request.outputFile().getParent());

        String command = applyTemplate(request);
        Process process = start(command);
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return ExportResult.failure("Exporter exited with code " + exitCode + formatOutput(output));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ExportResult.failure("Exporter was interrupted.");
        }

        if (!Files.exists(request.outputFile())) {
            return ExportResult.failure("Exporter finished without writing " + request.outputFile().getFileName());
        }
        return ExportResult.success();
    }

    private Process start(String command) throws IOException {
        return new ProcessBuilder("bash", "-lc", command)
                .redirectErrorStream(true)
                .start();
    }

    private String applyTemplate(ExportRequest request) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("stepFile", shellQuote(request.stepFile().toAbsolutePath().toString()));
        placeholders.put("outputFile", shellQuote(request.outputFile().toAbsolutePath().toString()));
        placeholders.put("definitionId", Integer.toString(request.product().definitionId()));
        placeholders.put("formationEntityId", nullableInteger(request.product().formationEntityId()));
        placeholders.put("productEntityId", nullableInteger(request.product().productEntityId()));
        placeholders.put("productId", shellQuote(nullableString(request.product().productId())));
        placeholders.put("productName", shellQuote(nullableString(request.product().name())));

        String command = commandTemplate;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            command = command.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return command;
    }

    private static String nullableInteger(Integer value) {
        return value == null ? "" : value.toString();
    }

    private static String nullableString(String value) {
        return value == null ? "" : value;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static String formatOutput(String output) {
        String trimmed = output == null ? "" : output.trim();
        if (trimmed.isEmpty()) {
            return ".";
        }
        return ": " + trimmed;
    }
}
