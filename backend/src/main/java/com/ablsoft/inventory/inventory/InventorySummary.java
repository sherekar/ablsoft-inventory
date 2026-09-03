package com.ablsoft.inventory.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventorySummary(long totalProducts, long totalEntries, BigDecimal totalInventoryValue,
                               BigDecimal averageStockAgeDays, String currency, LocalDate asOfDate) { }
