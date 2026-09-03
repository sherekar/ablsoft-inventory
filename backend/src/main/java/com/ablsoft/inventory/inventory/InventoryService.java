package com.ablsoft.inventory.inventory;

import com.ablsoft.inventory.error.InvalidRequestException;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryService {
    private static final Set<String> SORT_FIELDS = Set.of(
        "sku", "productName", "category", "purchaseDate", "unitPrice", "quantity", "stockAgeDays");
    private final InventoryRepository repository;
    private final Clock clock;
    private final String currency;

    public InventoryService(InventoryRepository repository, Clock clock,
                            @Value("${inventory.currency}") String currency) {
        this.repository = repository;
        this.clock = clock;
        this.currency = java.util.Currency.getInstance(currency).getCurrencyCode();
    }

    public PageResponse<InventoryResponse> list(int page, int size, String field, String direction) {
        if (page < 0 || size < 1 || size > 100 || !SORT_FIELDS.contains(field)
            || !("asc".equalsIgnoreCase(direction) || "desc".equalsIgnoreCase(direction))) {
            throw new InvalidRequestException("Use page >= 0, size 1–100, a supported sort field and asc/desc direction.");
        }
        Sort.Direction order = Sort.Direction.fromString(direction);
        if ("stockAgeDays".equals(field)) {
            field = "purchaseDate";
            order = order == Sort.Direction.ASC ? Sort.Direction.DESC : Sort.Direction.ASC;
        }
        LocalDate today = LocalDate.now(clock);
        var pageable = PageRequest.of(page, size, Sort.by(order, field).and(Sort.by("id")));
        return PageResponse.from(repository.findAll(pageable).map(item -> InventoryResponse.from(item, today)));
    }

    public InventorySummary summary() {
        LocalDate today = LocalDate.now(clock);
        var result = repository.summarize(today);
        return new InventorySummary(result.getTotalProducts(), result.getTotalEntries(),
            result.getTotalInventoryValue().setScale(2, RoundingMode.HALF_UP),
            result.getAverageStockAgeDays().setScale(1, RoundingMode.HALF_UP), currency, today);
    }
}
