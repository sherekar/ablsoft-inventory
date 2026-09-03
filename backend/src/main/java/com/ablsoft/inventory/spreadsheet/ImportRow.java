package com.ablsoft.inventory.spreadsheet;

import com.ablsoft.inventory.inventory.InventoryItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ImportRow(int rowNumber, String sku, String productName, String category,
                        LocalDate purchaseDate, BigDecimal unitPrice, int quantity) {
    public record Key(String sku, LocalDate purchaseDate) { }
    public Key key() { return new Key(sku, purchaseDate); }
    public InventoryItem toEntity(Instant createdAt) {
        return new InventoryItem(sku, productName, category, purchaseDate, unitPrice, quantity, createdAt);
    }
}
