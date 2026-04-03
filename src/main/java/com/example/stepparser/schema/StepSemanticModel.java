package com.example.stepparser.schema;

import com.example.stepparser.model.StepFile;
import com.example.stepparser.model.StepSchemas;
import com.example.stepparser.schema.runtime.StepSchemaModel;

import java.util.List;
import java.util.Optional;

public record StepSemanticModel(
        StepFile source,
        StepSchemas schema,
        StepSchemaModel schemaModel,
        List<StepApplicationContext> applicationContexts,
        List<StepProduct> products
) {

    public StepSemanticModel {
        applicationContexts = List.copyOf(applicationContexts);
        products = List.copyOf(products);
    }

    public Optional<StepProduct> findProductById(String productId) {
        return products.stream()
                .filter(product -> product.id().equals(productId))
                .findFirst();
    }
}
