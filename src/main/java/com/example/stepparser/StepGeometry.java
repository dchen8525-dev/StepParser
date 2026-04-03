package com.example.stepparser;

import java.util.List;

record StepGeometry(
        String meshName,
        List<Float> positions,
        List<Integer> indices,
        float[] color,
        String warning
) {

    StepGeometry {
        positions = List.copyOf(positions);
        indices = List.copyOf(indices);
    }

    boolean hasTriangles() {
        return !positions.isEmpty() && !indices.isEmpty();
    }
}
