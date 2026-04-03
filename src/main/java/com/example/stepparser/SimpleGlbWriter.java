package com.example.stepparser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class SimpleGlbWriter {

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_VERSION = 2;
    private static final int JSON_CHUNK_TYPE = 0x4E4F534A;
    private static final int BIN_CHUNK_TYPE = 0x004E4942;

    private SimpleGlbWriter() {
    }

    static byte[] writeGeometry(StepGeometry geometry) {
        MeshData mesh = geometry.hasTriangles()
                ? MeshData.fromGeometry(geometry)
                : MeshData.placeholder(geometry);
        byte[] json = padJson(buildJson(mesh));
        byte[] binary = mesh.binaryChunk();

        ByteBuffer glb = ByteBuffer.allocate(12 + 8 + json.length + 8 + binary.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        glb.putInt(GLB_MAGIC);
        glb.putInt(GLB_VERSION);
        glb.putInt(glb.capacity());
        glb.putInt(json.length);
        glb.putInt(JSON_CHUNK_TYPE);
        glb.put(json);
        glb.putInt(binary.length);
        glb.putInt(BIN_CHUNK_TYPE);
        glb.put(binary);
        return glb.array();
    }

    private static String buildJson(MeshData mesh) {
        float[] color = mesh.color();
        return """
                {"asset":{"version":"2.0","generator":"step-parser-java"},
                "scene":0,
                "scenes":[{"nodes":[0]}],
                "nodes":[{"mesh":0,"name":%s}],
                "meshes":[{"name":%s,"primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"indices":2,"material":0,"mode":4}]}],
                "materials":[{"pbrMetallicRoughness":{"baseColorFactor":[%s,%s,%s,1.0],"metallicFactor":0.1,"roughnessFactor":0.85},"doubleSided":false}],
                "buffers":[{"byteLength":%d}],
                "bufferViews":[
                {"buffer":0,"byteOffset":0,"byteLength":%d,"target":34962},
                {"buffer":0,"byteOffset":%d,"byteLength":%d,"target":34962},
                {"buffer":0,"byteOffset":%d,"byteLength":%d,"target":34963}],
                "accessors":[
                {"bufferView":0,"componentType":5126,"count":%d,"type":"VEC3","min":[%s,%s,%s],"max":[%s,%s,%s]},
                {"bufferView":1,"componentType":5126,"count":%d,"type":"VEC3"},
                {"bufferView":2,"componentType":5123,"count":%d,"type":"SCALAR","min":[0],"max":[%d]}]}
                """.formatted(
                quote(mesh.meshName()),
                quote(mesh.meshName()),
                floatText(color[0]),
                floatText(color[1]),
                floatText(color[2]),
                mesh.binaryLength(),
                mesh.positionByteLength(),
                mesh.normalOffset(),
                mesh.normalByteLength(),
                mesh.indexOffset(),
                mesh.indexByteLength(),
                mesh.vertexCount(),
                floatText(mesh.minX()),
                floatText(mesh.minY()),
                floatText(mesh.minZ()),
                floatText(mesh.maxX()),
                floatText(mesh.maxY()),
                floatText(mesh.maxZ()),
                mesh.vertexCount(),
                mesh.indexCount(),
                mesh.maxIndex()
        ).replace("\n", "");
    }

    private static byte[] padJson(String json) {
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        int paddedLength = align4(raw.length);
        byte[] padded = new byte[paddedLength];
        System.arraycopy(raw, 0, padded, 0, raw.length);
        for (int index = raw.length; index < paddedLength; index++) {
            padded[index] = 0x20;
        }
        return padded;
    }

    private static int align4(int value) {
        return (value + 3) & ~3;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String floatText(float value) {
        String text = Float.toString(value);
        return text.contains(".") || text.contains("E") ? text : text + ".0";
    }

    private record MeshData(
            String meshName,
            float[] positions,
            float[] normals,
            short[] indices,
            float[] color,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ
    ) {

        private static MeshData fromGeometry(StepGeometry geometry) {
            float[] positions = toFloatArray(geometry.positions());
            short[] indices = toShortArray(geometry.indices());
            float[] normals = computeNormals(positions, indices);
            float[] bounds = computeBounds(positions);
            return new MeshData(
                    geometry.meshName(),
                    positions,
                    normals,
                    indices,
                    geometry.color(),
                    bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]
            );
        }

        private static MeshData placeholder(StepGeometry geometry) {
            int definitionId = Math.max(1, geometry.meshName().hashCode());
            float halfX = 0.35f + Math.floorMod(definitionId, 5) * 0.08f;
            float halfY = 0.28f + Math.floorMod(definitionId, 7) * 0.05f;
            float halfZ = 0.22f + Math.floorMod(definitionId, 3) * 0.09f;
            float[] positions = {
                    -halfX, -halfY, halfZ, halfX, -halfY, halfZ, halfX, halfY, halfZ, -halfX, halfY, halfZ,
                    halfX, -halfY, -halfZ, -halfX, -halfY, -halfZ, -halfX, halfY, -halfZ, halfX, halfY, -halfZ,
                    -halfX, halfY, halfZ, halfX, halfY, halfZ, halfX, halfY, -halfZ, -halfX, halfY, -halfZ,
                    -halfX, -halfY, -halfZ, halfX, -halfY, -halfZ, halfX, -halfY, halfZ, -halfX, -halfY, halfZ,
                    halfX, -halfY, halfZ, halfX, -halfY, -halfZ, halfX, halfY, -halfZ, halfX, halfY, halfZ,
                    -halfX, -halfY, -halfZ, -halfX, -halfY, halfZ, -halfX, halfY, halfZ, -halfX, halfY, -halfZ
            };
            short[] indices = {
                    0, 1, 2, 0, 2, 3,
                    4, 5, 6, 4, 6, 7,
                    8, 9, 10, 8, 10, 11,
                    12, 13, 14, 12, 14, 15,
                    16, 17, 18, 16, 18, 19,
                    20, 21, 22, 20, 22, 23
            };
            float[] normals = computeNormals(positions, indices);
            return new MeshData(
                    geometry.meshName(),
                    positions,
                    normals,
                    indices,
                    geometry.color(),
                    -halfX, -halfY, -halfZ, halfX, halfY, halfZ
            );
        }

        private static float[] toFloatArray(List<Float> values) {
            float[] result = new float[values.size()];
            for (int index = 0; index < values.size(); index++) {
                result[index] = values.get(index);
            }
            return result;
        }

        private static short[] toShortArray(List<Integer> values) {
            short[] result = new short[values.size()];
            for (int index = 0; index < values.size(); index++) {
                result[index] = values.get(index).shortValue();
            }
            return result;
        }

        private static float[] computeNormals(float[] positions, short[] indices) {
            float[] normals = new float[positions.length];
            for (int offset = 0; offset < indices.length; offset += 3) {
                int a = indices[offset] * 3;
                int b = indices[offset + 1] * 3;
                int c = indices[offset + 2] * 3;

                float abX = positions[b] - positions[a];
                float abY = positions[b + 1] - positions[a + 1];
                float abZ = positions[b + 2] - positions[a + 2];
                float acX = positions[c] - positions[a];
                float acY = positions[c + 1] - positions[a + 1];
                float acZ = positions[c + 2] - positions[a + 2];

                float nx = abY * acZ - abZ * acY;
                float ny = abZ * acX - abX * acZ;
                float nz = abX * acY - abY * acX;

                normals[a] += nx;
                normals[a + 1] += ny;
                normals[a + 2] += nz;
                normals[b] += nx;
                normals[b + 1] += ny;
                normals[b + 2] += nz;
                normals[c] += nx;
                normals[c + 1] += ny;
                normals[c + 2] += nz;
            }

            for (int offset = 0; offset < normals.length; offset += 3) {
                float nx = normals[offset];
                float ny = normals[offset + 1];
                float nz = normals[offset + 2];
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length == 0.0f) {
                    normals[offset + 2] = 1.0f;
                    continue;
                }
                normals[offset] = nx / length;
                normals[offset + 1] = ny / length;
                normals[offset + 2] = nz / length;
            }
            return normals;
        }

        private static float[] computeBounds(float[] positions) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (int offset = 0; offset < positions.length; offset += 3) {
                minX = Math.min(minX, positions[offset]);
                minY = Math.min(minY, positions[offset + 1]);
                minZ = Math.min(minZ, positions[offset + 2]);
                maxX = Math.max(maxX, positions[offset]);
                maxY = Math.max(maxY, positions[offset + 1]);
                maxZ = Math.max(maxZ, positions[offset + 2]);
            }
            return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
        }

        private int vertexCount() {
            return positions.length / 3;
        }

        private int indexCount() {
            return indices.length;
        }

        private int maxIndex() {
            int max = 0;
            for (short index : indices) {
                max = Math.max(max, index & 0xFFFF);
            }
            return max;
        }

        private int positionByteLength() {
            return positions.length * Float.BYTES;
        }

        private int normalOffset() {
            return positionByteLength();
        }

        private int normalByteLength() {
            return normals.length * Float.BYTES;
        }

        private int indexOffset() {
            return normalOffset() + normalByteLength();
        }

        private int indexByteLength() {
            return align4(indices.length * Short.BYTES);
        }

        private int binaryLength() {
            return indexOffset() + indexByteLength();
        }

        private byte[] binaryChunk() {
            ByteBuffer buffer = ByteBuffer.allocate(binaryLength()).order(ByteOrder.LITTLE_ENDIAN);
            for (float value : positions) {
                buffer.putFloat(value);
            }
            for (float value : normals) {
                buffer.putFloat(value);
            }
            for (short value : indices) {
                buffer.putShort(value);
            }
            while (buffer.position() % 4 != 0) {
                buffer.put((byte) 0);
            }
            return buffer.array();
        }
    }
}
