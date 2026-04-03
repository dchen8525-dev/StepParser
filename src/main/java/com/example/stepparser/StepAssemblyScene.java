package com.example.stepparser;

import java.util.List;

public record StepAssemblyScene(
        String sourceStepFile,
        List<String> schemaNames,
        List<SceneNode> roots,
        List<String> warnings
) {

    public StepAssemblyScene {
        schemaNames = List.copyOf(schemaNames);
        roots = List.copyOf(roots);
        warnings = List.copyOf(warnings);
    }

    public record SceneNode(
            String instanceId,
            Integer occurrenceEntityId,
            String occurrenceId,
            int definitionId,
            Integer formationEntityId,
            Integer productEntityId,
            String productId,
            String name,
            String description,
            String displayName,
            GlbAsset glb,
            List<SceneNode> children
    ) {

        public SceneNode {
            children = List.copyOf(children);
        }
    }

    public record GlbAsset(
            String fileName,
            String relativeUri,
            boolean exported,
            String error
    ) {
    }
}
