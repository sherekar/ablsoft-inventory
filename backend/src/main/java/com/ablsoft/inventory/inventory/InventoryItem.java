package com.ablsoft.inventory.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_item")
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "inventory_item_seq")
    @SequenceGenerator(name = "inventory_item_seq", sequenceName = "inventory_item_seq", allocationSize = 50)
    private Long id;
    @Column(nullable = false, length = 100)
    private String sku;
    @Column(nullable = false, length = 200)
    private String productName;
    @Column(nullable = false, length = 100)
    private String category;
    @Column(nullable = false)
    private LocalDate purchaseDate;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private Instant createdAt;

    protected InventoryItem() { }

    public InventoryItem(String sku, String productName, String category, LocalDate purchaseDate,
                         BigDecimal unitPrice, int quantity, Instant createdAt) {
        this.sku = sku;
        this.productName = productName;
        this.category = category;
        this.purchaseDate = purchaseDate;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
}
