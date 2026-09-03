CREATE SEQUENCE inventory_item_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE inventory_item (
    id BIGINT PRIMARY KEY DEFAULT nextval('inventory_item_seq'),
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    purchase_date DATE NOT NULL,
    unit_price NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_inventory_sku_purchase_date UNIQUE (sku, purchase_date),
    CONSTRAINT ck_inventory_sku_normalized CHECK (sku = upper(trim(sku)))
);

CREATE INDEX ix_inventory_purchase_date ON inventory_item (purchase_date, id);
