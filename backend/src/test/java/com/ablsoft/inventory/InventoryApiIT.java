package com.ablsoft.inventory;

import static com.ablsoft.inventory.WorkbookFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ablsoft.inventory.inventory.InventoryRepository;
import com.ablsoft.inventory.spreadsheet.XlsxInventoryParser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(InventoryApiIT.FixedTime.class)
class InventoryApiIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary
        Clock testClock() { return Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC); }
    }

    @Autowired MockMvc mvc;
    @Autowired InventoryRepository repository;
    @MockitoSpyBean XlsxInventoryParser parser;

    @BeforeEach
    void resetDatabase() { repository.deleteAllInBatch(); }

    @Test
    void evaluatesWorkbookBeforeStartingDatabaseTransaction() throws Exception {
        var file = workbook(w -> {});
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(parser).parse(file);
        mvc.perform(multipart("/api/inventory/imports").file(file)).andExpect(status().isCreated());
    }

    @Test
    void preservesFrameworkHttpErrorsAndHeaders() throws Exception {
        mvc.perform(delete("/api/inventory")).andExpect(status().isMethodNotAllowed())
            .andExpect(header().exists("Allow"));
        mvc.perform(post("/api/inventory/imports").contentType("application/json").content("{}"))
            .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/missing")).andExpect(status().isNotFound());
    }

    @Test
    void importsAndReturnsAccurateSummaryAndSortedPages() throws Exception {
        var file = workbook(w -> {
            addRow(w, 2, "001-A", "2026-08-14", 7.75, 4);
            addRow(w, 3, "002-B", "2026-09-03", 5, 1);
        });
        mvc.perform(multipart("/api/inventory/imports").file(file)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.importedRows").value(3));
        mvc.perform(get("/api/inventory/summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalProducts").value(2)).andExpect(jsonPath("$.totalEntries").value(3))
            .andExpect(jsonPath("$.totalInventoryValue").value(61.0))
            .andExpect(jsonPath("$.averageStockAgeDays").value(10.0));
        mvc.perform(get("/api/inventory").param("size", "1").param("sort", "stockAgeDays").param("direction", "desc"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].purchaseDate").value("2026-08-14"))
            .andExpect(jsonPath("$.content[0].stockAgeDays").value(20)).andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/inventory").param("size", "1").param("page", "1").param("sort", "stockAgeDays").param("direction", "desc"))
            .andExpect(jsonPath("$.content[0].stockAgeDays").value(10));
    }

    @Test
    void duplicateAndInvalidImportsSaveNoRows() throws Exception {
        mvc.perform(multipart("/api/inventory/imports").file(workbook(w -> {}))).andExpect(status().isCreated());
        var duplicate = workbook(w -> addRow(w, 2, "NEW", "2026-08-01", 10, 1));
        mvc.perform(multipart("/api/inventory/imports").file(duplicate)).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].row").value(2));
        assertThat(repository.count()).isEqualTo(1);
        var invalid = workbook(w -> {
            w.getSheetAt(0).getRow(1).getCell(0).setCellValue("VALID");
            addRow(w, 2, "BAD", "2026-08-01", -1, 1);
        });
        mvc.perform(multipart("/api/inventory/imports").file(invalid)).andExpect(status().isUnprocessableEntity());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void concurrentOverlappingImportsLeaveOneCompleteBatch() throws Exception {
        var first = workbook(w -> addRow(w, 2, "FIRST", "2026-08-01", 1, 1));
        var second = workbook(w -> addRow(w, 2, "SECOND", "2026-08-01", 1, 1));
        var a = CompletableFuture.supplyAsync(() -> uploadStatus(first));
        var b = CompletableFuture.supplyAsync(() -> uploadStatus(second));
        var statuses = java.util.List.of(a.join(), b.join());
        assertThat(statuses).contains(201);
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses).allMatch(s -> s == 201 || s == 409 || s == 422);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void emptySummaryAndInvalidRequestsAreHandled() throws Exception {
        mvc.perform(get("/api/inventory/summary")).andExpect(jsonPath("$.totalProducts").value(0))
            .andExpect(jsonPath("$.averageStockAgeDays").value(0));
        mvc.perform(get("/api/inventory").param("sort", "createdAt")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/inventory").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/inventory").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/inventory").param("size", "x")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/inventory/imports")).andExpect(status().isBadRequest());
    }

    @Test
    void documentsInventoryAndMultipartUploadOnCurrentServer() throws Exception {
        var json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var spec = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        assertThat(spec.at("/info/title").asText()).isEqualTo("ABLSoft Inventory API");
        assertThat(spec.at("/servers/0/url").asText()).isEqualTo("/");
        assertThat(spec.path("paths").size()).isEqualTo(3);
        var upload = spec.at("/paths/~1api~1inventory~1imports/post");
        var request = upload.at("/requestBody/content/multipart~1form-data/schema");
        if (request.has("$ref")) request = spec.at(request.get("$ref").asText().substring(1));
        assertThat(request.at("/properties/file/type").asText()).isEqualTo("string");
        assertThat(request.at("/properties/file/format").asText()).isEqualTo("binary");
        assertThat(request.path("required").toString()).contains("file");
        assertThat(upload.at("/responses/201/content/application~1json/schema/$ref").asText())
            .isEqualTo("#/components/schemas/ImportResult");
        assertThat(upload.at("/responses/422/$ref").asText())
            .isEqualTo("#/components/responses/ImportValidationFailed");
        assertThat(spec.at("/components/responses/ImportValidationFailed/content/application~1problem+json/example/errors/0/row").asInt())
            .isEqualTo(3);
        assertThat(spec.at("/paths/~1api~1inventory~1summary/get/responses").has("422")).isFalse();
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    private int uploadStatus(MockMultipartFile file) {
        try { return mvc.perform(multipart("/api/inventory/imports").file(file)).andReturn().getResponse().getStatus(); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
}
