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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
