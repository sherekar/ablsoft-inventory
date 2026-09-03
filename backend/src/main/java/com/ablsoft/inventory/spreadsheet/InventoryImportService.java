package com.ablsoft.inventory.spreadsheet;

import com.ablsoft.inventory.inventory.InventoryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InventoryImportService {
    private final XlsxInventoryParser parser;
    private final InventoryRepository repository;
    private final Clock clock;
    private final int maxErrors;
    private final TransactionTemplate transactions;

    public InventoryImportService(XlsxInventoryParser parser, InventoryRepository repository, Clock clock,
                                   PlatformTransactionManager transactionManager,
                                   @Value("${inventory.import.max-errors}") int maxErrors) {
        this.parser = parser;
        this.repository = repository;
        this.clock = clock;
        this.maxErrors = maxErrors;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public ImportResult importFile(MultipartFile file) {
        List<ImportRow> rows = parser.parse(file);
        // Workbook parsing and formula evaluation do not hold a database transaction open.
        return transactions.execute(status -> persist(rows));
    }

    private ImportResult persist(List<ImportRow> rows) {
        LocalDate earliest = rows.stream().map(ImportRow::purchaseDate).min(LocalDate::compareTo).orElseThrow();
        LocalDate latest = rows.stream().map(ImportRow::purchaseDate).max(LocalDate::compareTo).orElseThrow();
        var existing = new HashSet<ImportRow.Key>();
        // One lookup for the upload, instead of one database round trip per row.
        repository.findExistingKeys(rows.stream().map(ImportRow::sku).distinct().toList(), earliest, latest)
            .forEach(key -> existing.add(new ImportRow.Key(key.getSku(), key.getPurchaseDate())));
        List<RowError> duplicates = rows.stream().filter(row -> existing.contains(row.key()))
            .map(row -> new RowError(row.rowNumber(), "Product SKU", "SKU and purchase date already exist in inventory."))
            .toList();
        if (!duplicates.isEmpty()) {
            throw new ImportValidationException(duplicates.stream().limit(maxErrors).toList(), duplicates.size());
        }
        var createdAt = clock.instant();
        repository.saveAllAndFlush(rows.stream().map(row -> row.toEntity(createdAt)).toList());
        return new ImportResult(rows.size(), "Import complete. " + rows.size() + " inventory rows were added.");
    }

    public record ImportResult(int importedRows, String message) { }
}
