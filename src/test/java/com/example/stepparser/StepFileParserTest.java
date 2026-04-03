package com.example.stepparser;

import com.example.stepparser.model.StepEntityInstance;
import com.example.stepparser.model.StepListValue;
import com.example.stepparser.model.StepReferenceValue;
import com.example.stepparser.model.StepStringValue;
import com.example.stepparser.parse.StepParseException;
import com.example.stepparser.schema.StepSemanticException;
import com.example.stepparser.schema.meta.StepSchemaLoader;
import com.example.stepparser.schema.runtime.StepEntityReference;
import com.example.stepparser.schema.runtime.StepSchemaModel;
import com.example.stepparser.schema.runtime.StepTypedEntity;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepFileParserTest {

    @Test
    void parsesAp203StyleFile() {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('CONFIGURATION CONTROL DESIGN'),'2;1');
                FILE_NAME('sample_ap203.step','2026-04-03T00:00:00',('author'),('org'),'parser','system','auth');
                FILE_SCHEMA(('CONFIG_CONTROL_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('configuration controlled 3d designs of mechanical parts and assemblies');
                #20 = PRODUCT('ID-1','Bracket','',(#10));
                ENDSEC;
                END-ISO-10303-21;
                """;

        var file = StepFileParser.parse(content);

        assertEquals(1, file.header().schemaNames().size());
        assertEquals("CONFIG_CONTROL_DESIGN", file.header().schemaNames().getFirst());
        assertEquals(2, file.data().entities().size());

        StepEntityInstance product = file.data().findById(20).orElseThrow();
        StepListValue contextList = assertInstanceOf(StepListValue.class, product.parameters().get(3));
        StepReferenceValue reference = assertInstanceOf(StepReferenceValue.class, contextList.values().getFirst());

        assertNotNull(reference.target());
        assertEquals(10, reference.target().id());
    }

    @Test
    void parsesAp214AndAp242SchemaNames() {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('multi.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN','AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF'));
                ENDSEC;
                DATA;
                #1 = PRODUCT('A','B','C',$);
                ENDSEC;
                END-ISO-10303-21;
                """;

        var file = StepFileParser.parse(content);

        assertEquals(2, file.header().schemaNames().size());
        assertEquals("AUTOMOTIVE_DESIGN", file.header().schemaNames().get(0));
        assertEquals("AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF", file.header().schemaNames().get(1));
    }

    @Test
    void parsesStringsEscapedQuotesEnumsAndNumbers() {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('values.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #1 = TEST_ENTITY('Bob''s part',1,-2.5E+3,.T.,*,(1,2,#2),$);
                #2 = NEXT_ENTITY('ok',$,$,$);
                ENDSEC;
                END-ISO-10303-21;
                """;

        var file = StepFileParser.parse(content);

        StepEntityInstance entity = file.data().findById(1).orElseThrow();
        StepStringValue name = assertInstanceOf(StepStringValue.class, entity.parameters().getFirst());
        assertEquals("Bob's part", name.value());
    }

    @Test
    void throwsOnMalformedInput() {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_SCHEMA(('CONFIG_CONTROL_DESIGN'));
                ENDSEC;
                DATA;
                #1 = PRODUCT('bad');
                END-ISO-10303-21;
                """;

        assertThrows(StepParseException.class, () -> StepFileParser.parse(content));
    }

    @Test
    void mapsSemanticProductsAndSchemaFamily() {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('semantic.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('automotive design');
                #20 = PRODUCT('P-100','Bracket','Main bracket',(#10));
                ENDSEC;
                END-ISO-10303-21;
                """;

        var model = StepFileParser.parseSemantic(content);

        assertEquals("AP214", model.schema().name());
        assertEquals(1, model.applicationContexts().size());
        assertEquals(1, model.products().size());
        assertEquals("P-100", model.products().getFirst().id());
        assertEquals("automotive design", model.products().getFirst().contexts().getFirst().description());
    }

    @Test
    void semanticMappingFailsForWrongProductContextType() {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('semantic-bad.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('CONFIG_CONTROL_DESIGN'));
                ENDSEC;
                DATA;
                #10 = PRODUCT('CTX','Not a context','', $);
                #20 = PRODUCT('P-100','Bracket','Main bracket',(#10));
                ENDSEC;
                END-ISO-10303-21;
                """;

        assertThrows(StepSemanticException.class, () -> StepFileParser.parseSemantic(content));
    }

    @Test
    void mapsEntitiesThroughSchemaRuntime() {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('schema.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('CONFIG_CONTROL_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('mechanical design');
                #11 = PRODUCT_DEFINITION_CONTEXT('part definition',#10,'design');
                #20 = PRODUCT('P-100','Bracket','Main bracket',(#10));
                #30 = PRODUCT_DEFINITION_FORMATION('F-1','',#20);
                #40 = PRODUCT_DEFINITION('D-1','release',#30,#11);
                ENDSEC;
                END-ISO-10303-21;
                """;

        StepSchemaModel model = StepFileParser.parseWithSchema(content);

        assertEquals("AP203", model.schemaDefinition().name());
        assertEquals(5, model.entities().size());

        StepTypedEntity productDefinition = model.findEntity(40).orElseThrow();
        StepEntityReference formationRef = assertInstanceOf(
                StepEntityReference.class,
                productDefinition.attributes().get("formation")
        );
        assertEquals(30, formationRef.referenceId());
        assertEquals("PRODUCT_DEFINITION_FORMATION", formationRef.targetEntityType());
    }

    @Test
    void schemaRuntimeValidationRejectsWrongReferenceTarget() {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('schema-bad.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('CONFIG_CONTROL_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('mechanical design');
                #20 = PRODUCT('P-100','Bracket','Main bracket',(#10));
                #30 = PRODUCT_DEFINITION_FORMATION('F-1','',#10);
                ENDSEC;
                END-ISO-10303-21;
                """;

        assertThrows(StepSemanticException.class, () -> StepFileParser.parseWithSchema(content));
    }

    @Test
    void loadsBuiltinSchemaAndGeneratesJavaTypes() throws Exception {
        var definition = StepSchemaLoader.loadBuiltin("AP214");
        assertTrue(definition.findEntity("PRODUCT").isPresent());
        assertTrue(definition.findEntity("ADVANCED_BREP_SHAPE_REPRESENTATION").isPresent());

        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('generate.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('automotive design');
                #20 = PRODUCT('P-100','Bracket','Main bracket',(#10));
                ENDSEC;
                END-ISO-10303-21;
                """;

        Path output = Path.of("target/generated-test-sources");
        var generated = StepFileParser.generateSchemaTypes(content, output, "com.example.stepparser.generated");

        assertTrue(generated.stream().anyMatch(path -> path.getFileName().toString().equals("Product.java")));
        assertTrue(Files.exists(output.resolve("com/example/stepparser/generated/ap214/Product.java")));
    }

    @Test
    void schemaLoaderSupportsSubtypeInheritance() {
        var definition = StepSchemaLoader.loadBuiltin("AP242");

        var advancedBrep = definition.findEntity("ADVANCED_BREP_SHAPE_REPRESENTATION").orElseThrow();
        assertEquals("SHAPE_REPRESENTATION", advancedBrep.supertype());
        assertEquals(3, definition.resolveAttributes("ADVANCED_BREP_SHAPE_REPRESENTATION").size());
        assertEquals(3, definition.resolveExplicitAttributes("ADVANCED_BREP_SHAPE_REPRESENTATION").size());
        assertTrue(definition.isAssignable("SHAPE_REPRESENTATION", "ADVANCED_BREP_SHAPE_REPRESENTATION"));
    }

    @Test
    void schemaRuntimeMapsInheritedAttributes() {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('subtype.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF'));
                ENDSEC;
                DATA;
                #10 = ADVANCED_BREP_SHAPE_REPRESENTATION('rep',(),('ctx'));
                ENDSEC;
                END-ISO-10303-21;
                """;

        StepSchemaModel model = StepFileParser.parseWithSchema(content);
        StepTypedEntity entity = model.findEntity(10).orElseThrow();

        assertEquals("rep", entity.attributes().get("name"));
        assertTrue(entity.attributes().containsKey("items"));
        assertTrue(entity.attributes().containsKey("context_of_items"));
    }

    @Test
    void schemaLoaderParsesEnumSelectAndBoundsMetadata() {
        var definition = StepSchemaLoader.loadBuiltin("AP214");

        var protocol = definition.findEntity("APPLICATION_PROTOCOL_DEFINITION").orElseThrow();
        assertEquals("STRING", protocol.attributes().getFirst().type().kind().name());
        assertEquals("NUMBER", protocol.attributes().get(2).type().kind().name());

        var productDefinitionContext = definition.findEntity("PRODUCT_DEFINITION_CONTEXT").orElseThrow();
        assertEquals("APPLICATION_CONTEXT_ELEMENT", productDefinitionContext.supertype());
        assertEquals(3, definition.resolveExplicitAttributes("PRODUCT_DEFINITION_CONTEXT").size());
        assertEquals("REFERENCE", definition.resolveExplicitAttributes("PRODUCT_DEFINITION_CONTEXT").get(1).type().kind().name());

        var category = definition.findEntity("PRODUCT_RELATED_PRODUCT_CATEGORY").orElseThrow();
        assertEquals(Integer.valueOf(1), category.attributes().get(2).type().minSize());

        var shapeRepresentation = definition.findEntity("SHAPE_REPRESENTATION").orElseThrow();
        assertTrue(definition.findEntity("REPRESENTATION").orElseThrow().isAbstract());
        assertEquals("GENERIC", definition.resolveExplicitAttributes("SHAPE_REPRESENTATION").get(1).type().itemType().kind().name());

        var shapeDefinitionRelationship = definition.findEntity("SHAPE_DEFINITION_REPRESENTATION").orElseThrow();
        assertEquals(3, shapeDefinitionRelationship.attributes().size());
        assertEquals("DERIVED", shapeDefinitionRelationship.attributes().get(2).kind().name());
    }

    @Test
    void schemaRuntimeValidatesEnumSelectAndListBounds() {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('valid-complex.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('automotive design');
                #11 = APPLICATION_PROTOCOL_DEFINITION('international standard','schema',2024,#10);
                #20 = PRODUCT('P-100','Bracket','Main bracket',(#10));
                #30 = PRODUCT_DEFINITION_FORMATION('F-1','',#20);
                #31 = PRODUCT_DEFINITION_CONTEXT('part definition',#10,'design');
                #40 = PRODUCT_DEFINITION('D-1','release',#30,#31);
                #50 = PRODUCT_DEFINITION_SHAPE('shape','',#40);
                #60 = SHAPE_REPRESENTATION('rep',('tag',#40,#50),('ctx'));
                #70 = PRODUCT_RELATED_PRODUCT_CATEGORY('assembly','',(#20));
                ENDSEC;
                END-ISO-10303-21;
                """;

        StepSchemaModel model = StepFileParser.parseWithSchema(content);
        StepTypedEntity protocol = model.findEntity(11).orElseThrow();
        StepTypedEntity shapeRepresentation = model.findEntity(60).orElseThrow();
        StepTypedEntity category = model.findEntity(70).orElseThrow();

        assertEquals("international standard", protocol.attributes().get("status"));
        assertEquals(3, ((java.util.List<?>) shapeRepresentation.attributes().get("items")).size());
        assertEquals(1, ((java.util.List<?>) category.attributes().get("products")).size());
        assertNull(protocol.attributes().get("missing"));
    }

    @Test
    void schemaRuntimeRejectsInvalidEnumAndListBounds() {
        String badType = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('bad-type.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('automotive design');
                #11 = APPLICATION_PROTOCOL_DEFINITION('draft international standard','schema','2024',#10);
                ENDSEC;
                END-ISO-10303-21;
                """;

        String badBound = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('bad-bound.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #70 = PRODUCT_RELATED_PRODUCT_CATEGORY('assembly','',());
                ENDSEC;
                END-ISO-10303-21;
                """;

        assertThrows(StepSemanticException.class, () -> StepFileParser.parseWithSchema(badType));
        assertThrows(StepSemanticException.class, () -> StepFileParser.parseWithSchema(badBound));
    }

    @Test
    void schemaRuntimeAllowsSubtypeReferenceWhereSupertypeIsExpected() {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('poly-ref.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF'));
                ENDSEC;
                DATA;
                #5 = APPLICATION_CONTEXT('design');
                #10 = PRODUCT_DEFINITION_CONTEXT('part definition',#5,'design');
                #20 = PRODUCT('P-100','Bracket','Main bracket',$);
                #30 = PRODUCT_DEFINITION_FORMATION('F-1','',#20);
                #40 = PRODUCT_DEFINITION('D-1','release',#30,#10);
                #50 = PRODUCT_DEFINITION_SHAPE('shape','',#40);
                #60 = ADVANCED_BREP_SHAPE_REPRESENTATION('rep',(),('ctx'));
                #70 = SHAPE_DEFINITION_REPRESENTATION(#50,#60);
                ENDSEC;
                END-ISO-10303-21;
                """;

        StepSchemaModel model = StepFileParser.parseWithSchema(content);
        StepTypedEntity relationship = model.findEntity(70).orElseThrow();
        StepEntityReference usedRepresentation = assertInstanceOf(
                StepEntityReference.class,
                relationship.attributes().get("used_representation")
        );

        assertEquals(60, usedRepresentation.referenceId());
        assertEquals("ADVANCED_BREP_SHAPE_REPRESENTATION", usedRepresentation.targetEntityType());
    }

    @Test
    void schemaRuntimeRejectsAbstractEntityInstantiation() {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('abstract.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #10 = REPRESENTATION('rep',(),('ctx'));
                ENDSEC;
                END-ISO-10303-21;
                """;

        assertThrows(StepSemanticException.class, () -> StepFileParser.parseWithSchema(content));
    }

    @Test
    void printsAssemblyTreeFromNextAssemblyUsageOccurrences() {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('assembly.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('automotive design');
                #11 = PRODUCT_DEFINITION_CONTEXT('part definition',#10,'design');
                #20 = PRODUCT('ROOT-ID','Root Assembly','',(#10));
                #21 = PRODUCT('CHILD-A','Child A','',(#10));
                #22 = PRODUCT('CHILD-B','Child B','',(#10));
                #30 = PRODUCT_DEFINITION_FORMATION('F-ROOT','',#20);
                #31 = PRODUCT_DEFINITION_FORMATION('F-A','',#21);
                #32 = PRODUCT_DEFINITION_FORMATION('F-B','',#22);
                #40 = PRODUCT_DEFINITION('ROOT-DEF','',#30,#11);
                #41 = PRODUCT_DEFINITION('A-DEF','',#31,#11);
                #42 = PRODUCT_DEFINITION('B-DEF','',#32,#11);
                #90 = NEXT_ASSEMBLY_USAGE_OCCURRENCE('NAUO-1','','',#40,#41,$);
                #91 = NEXT_ASSEMBLY_USAGE_OCCURRENCE('NAUO-2','','',#40,#42,$);
                ENDSEC;
                END-ISO-10303-21;
                """;

        var file = StepFileParser.parse(content);

        List<StepAssemblyTree.AssemblyNode> roots = StepAssemblyTree.roots(file.data());
        String formatted = StepAssemblyTree.format(file.data());

        assertEquals(1, roots.size());
        assertEquals("Root Assembly", roots.getFirst().product().name());
        assertEquals(2, roots.getFirst().children().size());
        assertTrue(formatted.contains("Assembly tree:"));
        assertTrue(formatted.contains("#40 Root Assembly [ROOT-ID] {product #20}"));
        assertTrue(formatted.contains("occurrence NAUO-1 (#90)"));
        assertTrue(formatted.contains("#41 Child A [CHILD-A] {product #21}"));
        assertTrue(formatted.contains("#42 Child B [CHILD-B] {product #22}"));
    }

    @Test
    void generatedTypesOnlyContainExplicitAttributes() throws Exception {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('generate-explicit.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('automotive design');
                #20 = PRODUCT('P-100','Bracket','Main bracket',(#10));
                ENDSEC;
                END-ISO-10303-21;
                """;

        Path output = Path.of("target/generated-explicit-sources");
        StepFileParser.generateSchemaTypes(content, output, "com.example.stepparser.generated");

        String generated = Files.readString(output.resolve("com/example/stepparser/generated/ap214/ShapeDefinitionRepresentation.java"));
        assertTrue(generated.contains("public record ShapeDefinitionRepresentation("));
        assertTrue(generated.contains("Integer definition"));
        assertTrue(generated.contains("Integer usedRepresentation"));
        assertTrue(!generated.contains("representationName"));
    }

    @Test
    void parsesAllExampleFiles() throws Exception {
        try (Stream<Path> paths = Files.list(Path.of("examples"))) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                var file = StepFileParser.parse(path);
                assertTrue(file.data().entities().size() > 0, () -> "Expected entities in " + path);
            }
        }
    }

    @Test
    void buildsFrontendAssemblySceneWithPerDefinitionGlbAsset() throws Exception {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('assembly.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('automotive design');
                #11 = PRODUCT_DEFINITION_CONTEXT('part definition',#10,'design');
                #20 = PRODUCT('ROOT-ID','Root Assembly','',(#10));
                #21 = PRODUCT('CHILD-A','Child A','',(#10));
                #30 = PRODUCT_DEFINITION_FORMATION('F-ROOT','',#20);
                #31 = PRODUCT_DEFINITION_FORMATION('F-A','',#21);
                #40 = PRODUCT_DEFINITION('ROOT-DEF','',#30,#11);
                #41 = PRODUCT_DEFINITION('A-DEF','',#31,#11);
                #90 = NEXT_ASSEMBLY_USAGE_OCCURRENCE('NAUO-1','','',#40,#41,$);
                ENDSEC;
                END-ISO-10303-21;
                """;

        Path stepFile = Files.createTempFile("assembly-scene", ".step");
        Files.writeString(stepFile, content);
        Path assetDirectory = Files.createTempDirectory("assembly-assets");

        StepGlbExporter exporter = request -> {
            Files.write(request.outputFile(), new byte[] { 'g', 'l', 'b' });
            return StepGlbExporter.ExportResult.success();
        };

        StepAssemblyScene scene = StepAssemblySceneBuilder.build(stepFile, assetDirectory, "/assets/test", exporter);

        assertEquals(1, scene.roots().size());
        StepAssemblyScene.SceneNode root = scene.roots().getFirst();
        assertEquals("Root Assembly", root.displayName());
        assertEquals("/assets/test/Root-Assembly-40.glb", root.glb().relativeUri());
        assertTrue(root.glb().exported());
        assertEquals(1, root.children().size());
        assertEquals("Child A", root.children().getFirst().displayName());
        assertTrue(Files.exists(assetDirectory.resolve("Root-Assembly-40.glb")));
        assertTrue(Files.exists(assetDirectory.resolve("Child-A-41.glb")));
    }

    @Test
    void serializesAssemblySceneAsJsonForFrontend() {
        StepAssemblyScene scene = new StepAssemblyScene(
                "/tmp/sample.step",
                List.of("AUTOMOTIVE_DESIGN"),
                List.of(new StepAssemblyScene.SceneNode(
                        "def-40",
                        null,
                        null,
                        40,
                        30,
                        20,
                        "ROOT-ID",
                        "Root Assembly",
                        "",
                        "Root Assembly",
                        new StepAssemblyScene.GlbAsset("Root-Assembly-40.glb", "/assets/test/Root-Assembly-40.glb", true, null),
                        List.of()
                )),
                List.of()
        );

        String json = StepJsonWriter.writeScene(scene);

        assertTrue(json.contains("\"sourceStepFile\":\"/tmp/sample.step\""));
        assertTrue(json.contains("\"schemaNames\":[\"AUTOMOTIVE_DESIGN\"]"));
        assertTrue(json.contains("\"definitionId\":40"));
        assertTrue(json.contains("\"relativeUri\":\"/assets/test/Root-Assembly-40.glb\""));
    }

    @Test
    void reportsSingleWarningWhenGlbExporterIsUnavailable() throws Exception {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('assembly.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('automotive design');
                #11 = PRODUCT_DEFINITION_CONTEXT('part definition',#10,'design');
                #20 = PRODUCT('ROOT-ID','Root Assembly','',(#10));
                #21 = PRODUCT('CHILD-A','Child A','',(#10));
                #30 = PRODUCT_DEFINITION_FORMATION('F-ROOT','',#20);
                #31 = PRODUCT_DEFINITION_FORMATION('F-A','',#21);
                #40 = PRODUCT_DEFINITION('ROOT-DEF','',#30,#11);
                #41 = PRODUCT_DEFINITION('A-DEF','',#31,#11);
                #90 = NEXT_ASSEMBLY_USAGE_OCCURRENCE('NAUO-1','','',#40,#41,$);
                ENDSEC;
                END-ISO-10303-21;
                """;

        Path stepFile = Files.createTempFile("assembly-scene-disabled", ".step");
        Files.writeString(stepFile, content);
        Path assetDirectory = Files.createTempDirectory("assembly-assets-disabled");

        StepAssemblyScene scene = StepAssemblySceneBuilder.build(
                stepFile,
                assetDirectory,
                "/assets/test",
                StepGlbExporter.disabled(StepParserConfiguration.missingGlbExporterMessage())
        );

        assertEquals(
                List.of(StepParserConfiguration.missingGlbExporterMessage()),
                scene.warnings()
        );
        assertFalse(scene.roots().getFirst().glb().exported());
        assertEquals(
                StepParserConfiguration.missingGlbExporterMessage(),
                scene.roots().getFirst().glb().error()
        );
        assertFalse(scene.roots().getFirst().children().getFirst().glb().exported());
    }

    @Test
    void resolvesGlbExporterCommandFromJvmProperty() throws Exception {
        String previousValue = System.getProperty(StepParserConfiguration.GLB_EXPORT_PROPERTY);
        try {
            System.setProperty(StepParserConfiguration.GLB_EXPORT_PROPERTY, "mock-export --input {stepFile}");

            String command = StepParserConfiguration.resolveGlbExportCommand(Path.of("").toAbsolutePath());

            assertEquals("mock-export --input {stepFile}", command);
        } finally {
            restoreSystemProperty(StepParserConfiguration.GLB_EXPORT_PROPERTY, previousValue);
        }
    }

    @Test
    void resolvesGlbExporterCommandFromLocalPropertiesFile() throws Exception {
        String previousValue = System.getProperty(StepParserConfiguration.GLB_EXPORT_PROPERTY);
        try {
            System.clearProperty(StepParserConfiguration.GLB_EXPORT_PROPERTY);
            Path workingDirectory = Files.createTempDirectory("step-parser-config");
            Path configFile = workingDirectory.resolve(StepParserConfiguration.CONFIG_FILE_NAME);
            Properties properties = new Properties();
            properties.setProperty(
                    StepParserConfiguration.GLB_EXPORT_PROPERTY,
                    "mock-export --definition {definitionId} --output {outputFile}"
            );
            try (var writer = Files.newBufferedWriter(configFile)) {
                properties.store(writer, "test");
            }

            String command = StepParserConfiguration.resolveGlbExportCommand(workingDirectory);

            assertEquals("mock-export --definition {definitionId} --output {outputFile}", command);
        } finally {
            restoreSystemProperty(StepParserConfiguration.GLB_EXPORT_PROPERTY, previousValue);
        }
    }

    @Test
    void javaGlbExporterWritesPlaceholderGlb() throws Exception {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('assembly.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('automotive design');
                #11 = PRODUCT_DEFINITION_CONTEXT('part definition',#10,'design');
                #20 = PRODUCT('ROOT-ID','Root Assembly','',(#10));
                #21 = PRODUCT('CHILD-ID','Child Part','',(#10));
                #30 = PRODUCT_DEFINITION_FORMATION('F-ROOT','',#20);
                #31 = PRODUCT_DEFINITION_FORMATION('F-CHILD','',#21);
                #40 = PRODUCT_DEFINITION('ROOT-DEF','',#30,#11);
                #41 = PRODUCT_DEFINITION('CHILD-DEF','',#31,#11);
                #90 = NEXT_ASSEMBLY_USAGE_OCCURRENCE('NAUO-1','','',#40,#41,$);
                ENDSEC;
                END-ISO-10303-21;
                """;

        Path stepFile = Files.createTempFile("assembly-scene-java-export", ".step");
        Files.writeString(stepFile, content);
        Path assetDirectory = Files.createTempDirectory("assembly-assets-java-export");

        StepAssemblyScene scene = StepAssemblySceneBuilder.build(
                stepFile,
                assetDirectory,
                "/assets/test",
                new JavaGlbExporter()
        );

        assertEquals(
                List.of("Using built-in Java GLB exporter. It approximates MANIFOLD_SOLID_BREP face loops and falls back to placeholders."),
                scene.warnings()
        );
        assertTrue(scene.roots().getFirst().glb().exported());
        Path glbFile = assetDirectory.resolve(scene.roots().getFirst().glb().fileName());
        assertTrue(Files.isRegularFile(glbFile));
        byte[] bytes = Files.readAllBytes(glbFile);
        assertEquals(0x67, bytes[0] & 0xFF);
        assertEquals(0x6C, bytes[1] & 0xFF);
        assertEquals(0x54, bytes[2] & 0xFF);
        assertEquals(0x46, bytes[3] & 0xFF);
    }

    @Test
    void javaGlbExporterApproximatesSimpleBrepFaceLoop() throws Exception {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('brep.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('automotive design');
                #11 = PRODUCT_DEFINITION_CONTEXT('part definition',#10,'design');
                #20 = PRODUCT('ROOT-ID','Root Assembly','',(#10));
                #30 = PRODUCT_DEFINITION_FORMATION('F-ROOT','',#20);
                #40 = PRODUCT_DEFINITION('ROOT-DEF','',#30,#11);
                #50 = PRODUCT_DEFINITION_SHAPE('shape','',#40);
                #60 = ADVANCED_BREP_SHAPE_REPRESENTATION('rep',(#61),('ctx'));
                #61 = MANIFOLD_SOLID_BREP('',#62);
                #62 = CLOSED_SHELL('',(#70));
                #70 = ADVANCED_FACE('',(#71),#99,.T.);
                #71 = FACE_OUTER_BOUND('',#72,.T.);
                #72 = EDGE_LOOP('',(#73,#74,#75));
                #73 = ORIENTED_EDGE('',*,*,#76,.T.);
                #74 = ORIENTED_EDGE('',*,*,#77,.T.);
                #75 = ORIENTED_EDGE('',*,*,#78,.T.);
                #76 = EDGE_CURVE('',#80,#81,#90,.T.);
                #77 = EDGE_CURVE('',#81,#82,#91,.T.);
                #78 = EDGE_CURVE('',#82,#80,#92,.T.);
                #80 = VERTEX_POINT('',#83);
                #81 = VERTEX_POINT('',#84);
                #82 = VERTEX_POINT('',#85);
                #83 = CARTESIAN_POINT('',(0.,0.,0.));
                #84 = CARTESIAN_POINT('',(1.,0.,0.));
                #85 = CARTESIAN_POINT('',(0.,1.,0.));
                #90 = DUMMY_EDGE_GEOMETRY('');
                #91 = DUMMY_EDGE_GEOMETRY('');
                #92 = DUMMY_EDGE_GEOMETRY('');
                #99 = DUMMY_SURFACE('');
                #100 = SHAPE_DEFINITION_REPRESENTATION(#50,#60);
                #110 = NEXT_ASSEMBLY_USAGE_OCCURRENCE('NAUO-1','','',#40,#40,$);
                ENDSEC;
                END-ISO-10303-21;
                """;

        Path stepFile = Files.createTempFile("assembly-scene-brep-export", ".step");
        Files.writeString(stepFile, content);
        Path assetDirectory = Files.createTempDirectory("assembly-assets-brep-export");

        StepAssemblyScene scene = StepAssemblySceneBuilder.build(
                stepFile,
                assetDirectory,
                "/assets/test",
                new JavaGlbExporter()
        );

        assertTrue(scene.roots().getFirst().glb().exported());
        Path glbFile = assetDirectory.resolve(scene.roots().getFirst().glb().fileName());
        byte[] bytes = Files.readAllBytes(glbFile);
        assertTrue(bytes.length > 600);
    }

    @Test
    void geometryExtractorAppliesAxisPlacementTransform() throws Exception {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('brep-transform.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('automotive design');
                #11 = PRODUCT_DEFINITION_CONTEXT('part definition',#10,'design');
                #20 = PRODUCT('ROOT-ID','Root Assembly','',(#10));
                #30 = PRODUCT_DEFINITION_FORMATION('F-ROOT','',#20);
                #40 = PRODUCT_DEFINITION('ROOT-DEF','',#30,#11);
                #50 = PRODUCT_DEFINITION_SHAPE('shape','',#40);
                #60 = ADVANCED_BREP_SHAPE_REPRESENTATION('rep',(#61,#62),('ctx'));
                #61 = AXIS2_PLACEMENT_3D('',#63,#64,#65);
                #62 = MANIFOLD_SOLID_BREP('',#66);
                #63 = CARTESIAN_POINT('',(10.,20.,30.));
                #64 = DIRECTION('',(0.,0.,1.));
                #65 = DIRECTION('',(0.,1.,0.));
                #66 = CLOSED_SHELL('',(#70));
                #70 = ADVANCED_FACE('',(#71),#99,.T.);
                #71 = FACE_OUTER_BOUND('',#72,.T.);
                #72 = EDGE_LOOP('',(#73,#74,#75));
                #73 = ORIENTED_EDGE('',*,*,#76,.T.);
                #74 = ORIENTED_EDGE('',*,*,#77,.T.);
                #75 = ORIENTED_EDGE('',*,*,#78,.T.);
                #76 = EDGE_CURVE('',#80,#81,#90,.T.);
                #77 = EDGE_CURVE('',#81,#82,#91,.T.);
                #78 = EDGE_CURVE('',#82,#80,#92,.T.);
                #80 = VERTEX_POINT('',#83);
                #81 = VERTEX_POINT('',#84);
                #82 = VERTEX_POINT('',#85);
                #83 = CARTESIAN_POINT('',(0.,0.,0.));
                #84 = CARTESIAN_POINT('',(1.,0.,0.));
                #85 = CARTESIAN_POINT('',(0.,1.,0.));
                #90 = DUMMY_EDGE_GEOMETRY('');
                #91 = DUMMY_EDGE_GEOMETRY('');
                #92 = DUMMY_EDGE_GEOMETRY('');
                #99 = DUMMY_SURFACE('');
                #100 = SHAPE_DEFINITION_REPRESENTATION(#50,#60);
                ENDSEC;
                END-ISO-10303-21;
                """;

        Path stepFile = Files.createTempFile("geometry-transform", ".step");
        Files.writeString(stepFile, content);

        StepGeometry geometry = new StepGeometryExtractor().extract(
                stepFile,
                new StepAssemblyTree.ProductDefinitionInfo(40, 30, 20, "ROOT-ID", "Root Assembly", null)
        );

        assertTrue(geometry.hasTriangles());
        assertEquals(List.of(10.0f, 20.0f, 30.0f), geometry.positions().subList(0, 3));
        assertEquals(List.of(10.0f, 21.0f, 30.0f), geometry.positions().subList(3, 6));
        assertEquals(List.of(9.0f, 20.0f, 30.0f), geometry.positions().subList(6, 9));
    }

    @Test
    void geometryExtractorTriangulatesConcaveFaceLoop() throws Exception {
        String content = """
                ISO-10303-21;
                HEADER;
                FILE_DESCRIPTION(('Example'),'2;1');
                FILE_NAME('brep-concave.step','2026-04-03T00:00:00',('a'),('o'),'p','s','a');
                FILE_SCHEMA(('AUTOMOTIVE_DESIGN'));
                ENDSEC;
                DATA;
                #10 = APPLICATION_CONTEXT('automotive design');
                #11 = PRODUCT_DEFINITION_CONTEXT('part definition',#10,'design');
                #20 = PRODUCT('ROOT-ID','Root Assembly','',(#10));
                #30 = PRODUCT_DEFINITION_FORMATION('F-ROOT','',#20);
                #40 = PRODUCT_DEFINITION('ROOT-DEF','',#30,#11);
                #50 = PRODUCT_DEFINITION_SHAPE('shape','',#40);
                #60 = ADVANCED_BREP_SHAPE_REPRESENTATION('rep',(#61),('ctx'));
                #61 = MANIFOLD_SOLID_BREP('',#66);
                #66 = CLOSED_SHELL('',(#70));
                #70 = ADVANCED_FACE('',(#71),#99,.T.);
                #71 = FACE_OUTER_BOUND('',#72,.T.);
                #72 = EDGE_LOOP('',(#73,#74,#75,#76,#77));
                #73 = ORIENTED_EDGE('',*,*,#83,.T.);
                #74 = ORIENTED_EDGE('',*,*,#84,.T.);
                #75 = ORIENTED_EDGE('',*,*,#85,.T.);
                #76 = ORIENTED_EDGE('',*,*,#86,.T.);
                #77 = ORIENTED_EDGE('',*,*,#87,.T.);
                #80 = VERTEX_POINT('',#90);
                #81 = VERTEX_POINT('',#91);
                #82 = VERTEX_POINT('',#92);
                #88 = VERTEX_POINT('',#93);
                #89 = VERTEX_POINT('',#94);
                #83 = EDGE_CURVE('',#80,#81,#100,.T.);
                #84 = EDGE_CURVE('',#81,#82,#101,.T.);
                #85 = EDGE_CURVE('',#82,#88,#102,.T.);
                #86 = EDGE_CURVE('',#88,#89,#103,.T.);
                #87 = EDGE_CURVE('',#89,#80,#104,.T.);
                #90 = CARTESIAN_POINT('',(0.,0.,0.));
                #91 = CARTESIAN_POINT('',(2.,0.,0.));
                #92 = CARTESIAN_POINT('',(2.,2.,0.));
                #93 = CARTESIAN_POINT('',(1.,1.,0.));
                #94 = CARTESIAN_POINT('',(0.,2.,0.));
                #99 = DUMMY_SURFACE('');
                #100 = DUMMY_EDGE_GEOMETRY('');
                #101 = DUMMY_EDGE_GEOMETRY('');
                #102 = DUMMY_EDGE_GEOMETRY('');
                #103 = DUMMY_EDGE_GEOMETRY('');
                #104 = DUMMY_EDGE_GEOMETRY('');
                #110 = SHAPE_DEFINITION_REPRESENTATION(#50,#60);
                ENDSEC;
                END-ISO-10303-21;
                """;

        Path stepFile = Files.createTempFile("geometry-concave", ".step");
        Files.writeString(stepFile, content);

        StepGeometry geometry = new StepGeometryExtractor().extract(
                stepFile,
                new StepAssemblyTree.ProductDefinitionInfo(40, 30, 20, "ROOT-ID", "Root Assembly", null)
        );

        assertTrue(geometry.hasTriangles());
        assertEquals(5, geometry.positions().size() / 3);
        assertEquals(9, geometry.indices().size());
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}
