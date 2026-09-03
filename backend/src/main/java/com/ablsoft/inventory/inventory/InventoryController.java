package com.ablsoft.inventory.inventory;

import com.ablsoft.inventory.spreadsheet.InventoryImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Workbook imports, inventory entries, and summary metrics")
@ApiResponse(responseCode = "500", ref = "#/components/responses/UnexpectedError")
public class InventoryController {
    private final InventoryService inventoryService;
    private final InventoryImportService importService;

    public InventoryController(InventoryService inventoryService, InventoryImportService importService) {
        this.inventoryService = inventoryService;
        this.importService = importService;
    }

    @GetMapping
    @Operation(summary = "Browse inventory entries", description = "Returns a sorted page. Stock age is calculated in whole calendar days; sorting uses the database ID to break ties.")
    @ApiResponse(responseCode = "200", description = "Inventory page", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    public PageResponse<InventoryResponse> list(
        @Parameter(description = "Zero-based page index", schema = @Schema(minimum = "0"))
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Entries per page", schema = @Schema(minimum = "1", maximum = "100"))
        @RequestParam(defaultValue = "10") int size,
        @Parameter(description = "Field to sort by", schema = @Schema(allowableValues = {"sku", "productName", "category", "purchaseDate", "unitPrice", "quantity", "stockAgeDays"}))
        @RequestParam(defaultValue = "purchaseDate") String sort,
        @Parameter(description = "Sort direction", schema = @Schema(allowableValues = {"asc", "desc"}))
        @RequestParam(defaultValue = "desc") String direction) {
        return inventoryService.list(page, size, sort, direction);
    }

    @GetMapping("/summary")
    @Operation(summary = "Get inventory summary", description = "Covers all entries: distinct SKU count, entry count, sum of unit price times quantity, and unweighted average stock age. Includes the display currency and calculation date.")
    @ApiResponse(responseCode = "200", description = "Full-inventory metrics; empty totals are zero", useReturnTypeSchema = true)
    public InventorySummary summary() { return inventoryService.summary(); }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Import an XLSX workbook", description = "Upload a standard, unencrypted XLSX file (maximum 5 MB and 10,000 data rows). The first worksheet requires Product SKU, Product Name, Category, Purchase Date, Unit Price, and Quantity headers. Formulas are evaluated; text dates use YYYY-MM-DD. SKUs are normalized to uppercase. Each SKU and purchase date must be unique within the file and database. Validation failure saves zero rows. See the README for the complete workbook contract.")
    @ApiResponse(responseCode = "201", description = "All rows imported successfully", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/ImportConflict")
    @ApiResponse(responseCode = "413", ref = "#/components/responses/FileTooLarge")
    @ApiResponse(responseCode = "415", ref = "#/components/responses/UnsupportedMediaType")
    @ApiResponse(responseCode = "422", ref = "#/components/responses/ImportValidationFailed")
    public InventoryImportService.ImportResult importFile(
        @Parameter(description = "XLSX workbook; sample files are provided in the samples directory")
        @RequestParam("file") MultipartFile file) {
        return importService.importFile(file);
    }
}
