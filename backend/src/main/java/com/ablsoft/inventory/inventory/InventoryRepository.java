package com.ablsoft.inventory.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<InventoryItem, Long> {
    interface ExistingKey {
        String getSku();
        LocalDate getPurchaseDate();
    }
    interface Summary {
        long getTotalProducts();
        long getTotalEntries();
        BigDecimal getTotalInventoryValue();
        BigDecimal getAverageStockAgeDays();
    }

    @Query("select i.sku as sku, i.purchaseDate as purchaseDate from InventoryItem i " +
           "where i.sku in :skus and i.purchaseDate between :fromDate and :toDate")
    List<ExistingKey> findExistingKeys(@Param("skus") Collection<String> skus,
                                     @Param("fromDate") LocalDate fromDate,
                                     @Param("toDate") LocalDate toDate);

    @Query(value = """
        SELECT count(DISTINCT sku) AS totalProducts,
               count(*) AS totalEntries,
               coalesce(sum(unit_price * quantity), 0) AS totalInventoryValue,
               coalesce(avg(CAST(:today AS date) - purchase_date), 0) AS averageStockAgeDays
        FROM inventory_item
        """, nativeQuery = true)
    Summary summarize(@Param("today") LocalDate today);
}
