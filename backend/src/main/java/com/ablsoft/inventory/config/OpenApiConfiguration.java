package com.ablsoft.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI inventoryOpenApi() {
        var problem = new ObjectSchema()
            .addProperty("type", new StringSchema().example("about:blank"))
            .addProperty("title", new StringSchema())
            .addProperty("status", new IntegerSchema())
            .addProperty("detail", new StringSchema())
            .addProperty("instance", new StringSchema().description("Request path, when available"));
        var rowError = new ObjectSchema()
            .addProperty("row", new IntegerSchema().description("One-based worksheet row"))
            .addProperty("column", new StringSchema())
            .addProperty("message", new StringSchema());
        var validationProblem = new ComposedSchema()
            .addAllOfItem(new Schema<>().$ref("#/components/schemas/ApiProblem"))
            .addAllOfItem(new ObjectSchema()
                .addProperty("errors", new ArraySchema().items(rowError).maxItems(100))
                .addProperty("totalErrors", new IntegerSchema().description("Total errors, including those beyond the display limit")));
        var validationResponse = problemResponse("Workbook validation or duplicate check failed; no rows were saved.", "ImportValidationProblem");
        validationResponse.getContent().get("application/problem+json").example(Map.of(
            "type", "about:blank", "title", "Import validation failed", "status", 422,
            "detail", "Import rejected. Correct the errors and try again. No rows were saved.",
            "errors", List.of(Map.of("row", 3, "column", "Product SKU", "message", "SKU and purchase date duplicate row 2.")),
            "totalErrors", 1));
        return new OpenAPI()
            .info(new Info().title("ABLSoft Inventory API").version("1.0.0")
                .description("Import XLSX inventory, browse stock, and view full-inventory totals. Imports add entries atomically; they never update existing entries."))
            // A relative server keeps Try it out on the current host and port, including Docker's proxy.
            .servers(List.of(new Server().url("/").description("Current application")))
            .components(new Components()
                .addSchemas("ApiProblem", problem)
                .addSchemas("ImportValidationProblem", validationProblem)
                .addResponses("BadRequest", problemResponse("Invalid parameters, missing file, or unreadable XLSX workbook.", "ApiProblem"))
                .addResponses("ImportConflict", problemResponse("Database write conflict, including concurrent duplicates; no rows were saved.", "ApiProblem"))
                .addResponses("FileTooLarge", problemResponse("Workbook exceeds the 5 MB upload limit.", "ApiProblem"))
                .addResponses("UnsupportedMediaType", problemResponse("Send the upload as multipart/form-data.", "ApiProblem"))
                .addResponses("ImportValidationFailed", validationResponse)
                .addResponses("UnexpectedError", problemResponse("Unexpected server error; details are logged on the server.", "ApiProblem")));
    }

    private ApiResponse problemResponse(String description, String schema) {
        return new ApiResponse().description(description).content(new Content().addMediaType(
            "application/problem+json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + schema))));
    }
}
