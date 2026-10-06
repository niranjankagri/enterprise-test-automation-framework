-- ShopEase Admin schema (H2). Recreated on every start: the demo app keeps no state between runs.

CREATE TABLE app_users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    salt          VARCHAR(50)  NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE customers (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(50)  NOT NULL,
    last_name  VARCHAR(50)  NOT NULL,
    email      VARCHAR(120) NOT NULL UNIQUE,
    phone      VARCHAR(20),
    city       VARCHAR(60),
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE products (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku      VARCHAR(30)    NOT NULL UNIQUE,
    name     VARCHAR(100)   NOT NULL,
    category VARCHAR(50)    NOT NULL,
    price    DECIMAL(10, 2) NOT NULL,
    stock    INT            NOT NULL,
    active   BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE orders (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT         NOT NULL REFERENCES customers (id),
    status      VARCHAR(20)    NOT NULL,
    total       DECIMAL(12, 2) NOT NULL,
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id BIGINT         NOT NULL REFERENCES products (id),
    quantity   INT            NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL
);

INSERT INTO products (sku, name, category, price, stock) VALUES
    ('LAP-1001', 'Ultrabook 14', 'Laptops', 1199.00, 25),
    ('LAP-1002', 'Workstation 16', 'Laptops', 2199.00, 10),
    ('MON-2001', '27-inch 4K Monitor', 'Monitors', 429.00, 40),
    ('MON-2002', '34-inch Ultrawide Monitor', 'Monitors', 649.00, 15),
    ('ACC-3001', 'Wireless Keyboard', 'Accessories', 79.00, 120),
    ('ACC-3002', 'Wireless Mouse', 'Accessories', 49.00, 150),
    ('ACC-3003', 'USB-C Dock', 'Accessories', 189.00, 60),
    ('AUD-4001', 'Noise-Cancelling Headphones', 'Audio', 299.00, 35);

INSERT INTO customers (first_name, last_name, email, phone, city) VALUES
    ('Ava', 'Patel', 'ava.patel@example.com', '+1-555-0101', 'Austin'),
    ('Liam', 'Chen', 'liam.chen@example.com', '+1-555-0102', 'Seattle'),
    ('Sofia', 'Garcia', 'sofia.garcia@example.com', '+1-555-0103', 'Denver');
