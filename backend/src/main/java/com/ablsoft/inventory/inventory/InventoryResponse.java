package com.ablsoft.inventory.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record InventoryResponse(long id, String sku, String productName, String category,
                                LocalDate purchaseDate, BigDecimal unitPrice, int quantity,
                                long stockAgeDays) {
    static InventoryResponse from(InventoryItem item, LocalDate today) {
        return new InventoryResponse(item.getId(), item.getSku(), item.getProductName(), item.getCategory(),
            item.getPurchaseDate(), item.getUnitPrice(), item.getQuantity(),
            ChronoUnit.DAYS.between(item.getPurchaseDate(), today));
    }
}
