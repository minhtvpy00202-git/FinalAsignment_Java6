-- Assignment Java 5 - SQL Server schema & seed (refactored)

SET NOCOUNT ON;
GO

IF DB_ID(N'ASM_Java5') IS NULL
BEGIN
    CREATE DATABASE ASM_Java5;
END
GO

USE ASM_Java5;
GO

/* =========================================================
   CLEANUP (drop theo thứ tự phụ thuộc)
   ========================================================= */
IF OBJECT_ID(N'dbo.notifications', N'U') IS NOT NULL DROP TABLE dbo.notifications;
IF OBJECT_ID(N'dbo.product_reviews', N'U') IS NOT NULL DROP TABLE dbo.product_reviews;
IF OBJECT_ID(N'dbo.cart_items', N'U') IS NOT NULL DROP TABLE dbo.cart_items;
IF OBJECT_ID(N'dbo.order_details', N'U') IS NOT NULL DROP TABLE dbo.order_details;
IF OBJECT_ID(N'dbo.product_sizes', N'U') IS NOT NULL DROP TABLE dbo.product_sizes;
IF OBJECT_ID(N'dbo.sizes', N'U') IS NOT NULL DROP TABLE dbo.sizes;
IF OBJECT_ID(N'dbo.orders', N'U') IS NOT NULL DROP TABLE dbo.orders;
IF OBJECT_ID(N'dbo.authorities', N'U') IS NOT NULL DROP TABLE dbo.authorities;
IF OBJECT_ID(N'dbo.roles', N'U') IS NOT NULL DROP TABLE dbo.roles;
IF OBJECT_ID(N'dbo.products', N'U') IS NOT NULL DROP TABLE dbo.products;
IF OBJECT_ID(N'dbo.categories', N'U') IS NOT NULL DROP TABLE dbo.categories;
IF OBJECT_ID(N'dbo.accounts', N'U') IS NOT NULL DROP TABLE dbo.accounts;
GO

/* =========================================================
   SCHEMA
   ========================================================= */
CREATE TABLE dbo.categories (
    id   VARCHAR(20)   NOT NULL PRIMARY KEY,
    name NVARCHAR(100) NOT NULL
);

CREATE TABLE dbo.products (
    id          INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    name        NVARCHAR(200)     NOT NULL,
    image       VARCHAR(255)      NULL,
    price       DECIMAL(12,2)     NOT NULL,
    discount    DECIMAL(5,2)      NULL,
    available   BIT               NOT NULL CONSTRAINT DF_products_available DEFAULT (1),
    quantity    INT               NULL,
    description NVARCHAR(2000)    NULL,
    create_date DATETIME2         NOT NULL CONSTRAINT DF_products_create_date DEFAULT (SYSDATETIME()),
    category_id VARCHAR(20)       NULL,
    is_delete   BIT               NOT NULL CONSTRAINT DF_products_is_delete DEFAULT (0),
    CONSTRAINT FK_products_categories
        FOREIGN KEY (category_id) REFERENCES dbo.categories(id)
);

CREATE TABLE dbo.sizes (
    id   INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    name NVARCHAR(10)      NOT NULL UNIQUE
);

CREATE TABLE dbo.product_sizes (
    id         INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    product_id INT               NOT NULL,
    size_id    INT               NOT NULL,
    quantity   INT               NOT NULL,
    CONSTRAINT FK_product_sizes_products
        FOREIGN KEY (product_id) REFERENCES dbo.products(id),
    CONSTRAINT FK_product_sizes_sizes
        FOREIGN KEY (size_id) REFERENCES dbo.sizes(id),
    CONSTRAINT UQ_product_sizes UNIQUE (product_id, size_id)
);

CREATE TABLE dbo.accounts (
    username  VARCHAR(50)   NOT NULL PRIMARY KEY,
    password  VARCHAR(255)  NOT NULL,
    fullname  NVARCHAR(100) NOT NULL,
    email     VARCHAR(100)  NOT NULL UNIQUE,
    phone     VARCHAR(20)   NOT NULL,
    address   NVARCHAR(255) NOT NULL,
    photo     VARCHAR(255)  NULL,
    activated BIT           NOT NULL CONSTRAINT DF_accounts_activated DEFAULT (1),
    is_delete BIT           NOT NULL CONSTRAINT DF_accounts_is_delete DEFAULT (0)
);

CREATE TABLE dbo.cart_items (
    id         BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    username   VARCHAR(50)          NOT NULL,
    product_id INT                  NOT NULL,
    size_id    INT                  NOT NULL,
    quantity   INT                  NOT NULL CONSTRAINT DF_cart_items_quantity DEFAULT (1),
    created_at DATETIME2            NOT NULL CONSTRAINT DF_cart_items_created_at DEFAULT (SYSDATETIME()),
    CONSTRAINT FK_cart_items_accounts
        FOREIGN KEY (username) REFERENCES dbo.accounts(username),
    CONSTRAINT FK_cart_items_products
        FOREIGN KEY (product_id) REFERENCES dbo.products(id),
    CONSTRAINT FK_cart_items_sizes
        FOREIGN KEY (size_id) REFERENCES dbo.sizes(id),
    CONSTRAINT UQ_cart_items UNIQUE (username, product_id, size_id)
);

CREATE TABLE dbo.roles (
    id   VARCHAR(20)  NOT NULL PRIMARY KEY,
    name NVARCHAR(50) NOT NULL
);

CREATE TABLE dbo.authorities (
    id       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    username VARCHAR(50)          NULL,
    role_id  VARCHAR(20)          NULL,
    CONSTRAINT FK_authorities_accounts
        FOREIGN KEY (username) REFERENCES dbo.accounts(username),
    CONSTRAINT FK_authorities_roles
        FOREIGN KEY (role_id) REFERENCES dbo.roles(id)
);

CREATE TABLE dbo.orders (
    id          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    address     NVARCHAR(255)        NOT NULL,
    status      VARCHAR(20)          NULL,
    create_date DATETIME2            NOT NULL CONSTRAINT DF_orders_create_date DEFAULT (SYSDATETIME()),
    username    VARCHAR(50)          NULL,
    CONSTRAINT FK_orders_accounts
        FOREIGN KEY (username) REFERENCES dbo.accounts(username)
);

CREATE TABLE dbo.notifications (
    id         BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    username   VARCHAR(50)          NOT NULL,
    order_id   BIGINT               NULL,
    title      NVARCHAR(200)        NOT NULL,
    content    NVARCHAR(1000)       NOT NULL,
    is_read    BIT                  NOT NULL CONSTRAINT DF_notifications_is_read DEFAULT (0),
    created_at DATETIME2            NOT NULL CONSTRAINT DF_notifications_created_at DEFAULT (SYSDATETIME()),
    CONSTRAINT FK_notifications_accounts
        FOREIGN KEY (username) REFERENCES dbo.accounts(username),
    CONSTRAINT FK_notifications_orders
        FOREIGN KEY (order_id) REFERENCES dbo.orders(id)
);

CREATE TABLE dbo.order_details (
    id        BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    price     DECIMAL(12,2)        NOT NULL,
    quantity  INT                  NOT NULL,
    size_id   INT                  NULL,
    size_name NVARCHAR(10)         NULL,
    order_id  BIGINT               NULL,
    product_id INT                 NULL,
    CONSTRAINT FK_order_details_orders
        FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
    CONSTRAINT FK_order_details_products
        FOREIGN KEY (product_id) REFERENCES dbo.products(id)
);

CREATE TABLE dbo.product_reviews (
    id             BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    username       VARCHAR(50)          NOT NULL,
    product_id     INT                  NOT NULL,
    order_id       BIGINT               NOT NULL,
    star_rating    INT                  NOT NULL,
    review_content NVARCHAR(2000)       NULL,
    images         NVARCHAR(2000)       NULL,
    created_at     DATETIME2            NOT NULL CONSTRAINT DF_product_reviews_created_at DEFAULT (SYSDATETIME()),
    CONSTRAINT FK_product_reviews_accounts
        FOREIGN KEY (username) REFERENCES dbo.accounts(username),
    CONSTRAINT FK_product_reviews_products
        FOREIGN KEY (product_id) REFERENCES dbo.products(id),
    CONSTRAINT FK_product_reviews_orders
        FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
    CONSTRAINT UQ_product_reviews UNIQUE (username, product_id, order_id)
);
GO

/* =========================================================
   INDEXES
   ========================================================= */
CREATE INDEX IX_products_category_id      ON dbo.products(category_id);
CREATE INDEX IX_product_sizes_product_id  ON dbo.product_sizes(product_id);
CREATE INDEX IX_product_sizes_size_id     ON dbo.product_sizes(size_id);
CREATE INDEX IX_orders_username           ON dbo.orders(username);
CREATE INDEX IX_order_details_order_id    ON dbo.order_details(order_id);
CREATE INDEX IX_order_details_product_id  ON dbo.order_details(product_id);
CREATE INDEX IX_notifications_username    ON dbo.notifications(username);
CREATE INDEX IX_notifications_order_id    ON dbo.notifications(order_id);
CREATE INDEX IX_cart_items_username       ON dbo.cart_items(username);
GO

/* =========================================================
   SEED DATA
   ========================================================= */
INSERT INTO dbo.roles (id, name) VALUES
('ADMIN', N'Quản trị'),
('USER',  N'Khách hàng');

INSERT INTO dbo.accounts (username, password, fullname, email, phone, address, photo, activated, is_delete) VALUES
('admin',  'admin123', N'Nguyễn Quản Trị', 'admin@shop.local',  '0910000001', N'1 Nguyễn Huệ, TP.HCM',       'admin.png', 1, 0),
('user01', '123456',   N'Trần Minh Anh',   'user01@shop.local', '0910000002', N'12 Trần Phú, Hà Nội',        'u01.png',   1, 0),
('user02', '123456',   N'Lê Thu Hà',       'user02@shop.local', '0910000003', N'25 Lê Lợi, Đà Nẵng',         'u02.png',   1, 0),
('user03', '123456',   N'Phạm Quốc Bảo',   'user03@shop.local', '0910000004', N'88 Nguyễn Văn Cừ, Cần Thơ',  'u03.png',   1, 0),
('user04', '123456',   N'Ngô Tuấn Kiệt',   'user04@shop.local', '0910000005', N'45 Lý Tự Trọng, TP.HCM',     'u04.png',   1, 0),
('user05', '123456',   N'Đỗ Khánh Ly',     'user05@shop.local', '0910000006', N'9 Quang Trung, Hải Phòng',   'u05.png',   1, 0);

INSERT INTO dbo.authorities (username, role_id) VALUES
('admin',  'ADMIN'),
('admin',  'USER'),
('user01', 'USER'),
('user02', 'USER'),
('user03', 'USER'),
('user04', 'USER'),
('user05', 'USER');

INSERT INTO dbo.categories (id, name) VALUES
('CAT01', N'Áo thun'),
('CAT02', N'Áo sơ mi'),
('CAT03', N'Áo khoác'),
('CAT04', N'Quần jeans'),
('CAT05', N'Quần short'),
('CAT06', N'Váy/Đầm'),
('CAT07', N'Giày sneaker'),
('CAT08', N'Giày da'),
('CAT09', N'Túi xách'),
('CAT10', N'Phụ kiện');

INSERT INTO dbo.sizes (name) VALUES
(N'S'),
(N'M'),
(N'L'),
(N'XL'),
(N'2XL'),
(N'3XL');

DECLARE @i INT = 1;
WHILE @i <= 200
BEGIN
    DECLARE @catIndex INT = ((@i - 1) % 10) + 1;
    DECLARE @cat VARCHAR(20) = 'CAT' + RIGHT('0' + CAST(@catIndex AS VARCHAR(2)), 2);

    INSERT INTO dbo.products (name, image, price, discount, available, quantity, description, create_date, category_id, is_delete)
    VALUES (
        N'Sản phẩm ' + CAST(@i AS NVARCHAR(10)),
        'p' + RIGHT('000' + CAST(@i AS VARCHAR(10)), 3) + '.jpg',
        CAST(99000 + (@i * 350) AS DECIMAL(12,2)),
        CASE WHEN @i % 7 = 0 THEN CAST(10 AS DECIMAL(5,2)) ELSE NULL END,
        1,
        100,
        N'Mô tả sản phẩm ' + CAST(@i AS NVARCHAR(10)),
        SYSDATETIME(),
        @cat,
        0
    );

    SET @i += 1;
END
GO

INSERT INTO dbo.product_sizes (product_id, size_id, quantity)
SELECT p.id, s.id, 5 + (p.id % 20)
FROM dbo.products p
JOIN dbo.sizes s ON s.name IN (N'S', N'M', N'L', N'XL');

INSERT INTO dbo.product_sizes (product_id, size_id, quantity)
SELECT p.id, s.id, 5 + (p.id % 20)
FROM dbo.products p
JOIN dbo.sizes s ON s.name = N'2XL'
WHERE p.id % 3 = 0;

INSERT INTO dbo.product_sizes (product_id, size_id, quantity)
SELECT p.id, s.id, 5 + (p.id % 20)
FROM dbo.products p
JOIN dbo.sizes s ON s.name = N'3XL'
WHERE p.id % 5 = 0;
GO

/* =========================================================
   MIGRATION: thêm phone/address cho DB đã tồn tại
   ========================================================= */
IF COL_LENGTH('dbo.accounts', 'phone') IS NULL
BEGIN
    ALTER TABLE dbo.accounts ADD phone VARCHAR(20) NULL;
END
GO

IF COL_LENGTH('dbo.accounts', 'address') IS NULL
BEGIN
    ALTER TABLE dbo.accounts ADD address NVARCHAR(255) NULL;
END
GO

UPDATE dbo.accounts SET phone = '0000000000' WHERE phone IS NULL OR LTRIM(RTRIM(phone)) = '';
UPDATE dbo.accounts SET address = N'Chưa cập nhật' WHERE address IS NULL OR LTRIM(RTRIM(address)) = '';
GO

ALTER TABLE dbo.accounts ALTER COLUMN phone VARCHAR(20) NOT NULL;
ALTER TABLE dbo.accounts ALTER COLUMN address NVARCHAR(255) NOT NULL;
GO

-- UPDATE ĐƠN VỊ HÀNH CHÍNH 08/04
IF OBJECT_ID('dbo.administrative_regions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.administrative_regions (
        id INT NOT NULL PRIMARY KEY,
        name NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NOT NULL,
        code_name NVARCHAR(255) NULL,
        code_name_en NVARCHAR(255) NULL
    );
END
GO

IF OBJECT_ID('dbo.administrative_units', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.administrative_units (
        id INT NOT NULL PRIMARY KEY,
        full_name NVARCHAR(255) NULL,
        full_name_en NVARCHAR(255) NULL,
        short_name NVARCHAR(255) NULL,
        short_name_en NVARCHAR(255) NULL,
        code_name NVARCHAR(255) NULL,
        code_name_en NVARCHAR(255) NULL
    );
END
GO

IF OBJECT_ID('dbo.provinces', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.provinces (
        code NVARCHAR(20) NOT NULL PRIMARY KEY,
        name NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NULL,
        full_name NVARCHAR(255) NOT NULL,
        full_name_en NVARCHAR(255) NULL,
        code_name NVARCHAR(255) NULL,
        administrative_unit_id INT NULL
    );
END
GO

IF OBJECT_ID('dbo.wards', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.wards (
        code NVARCHAR(20) NOT NULL PRIMARY KEY,
        name NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NULL,
        full_name NVARCHAR(255) NULL,
        full_name_en NVARCHAR(255) NULL,
        code_name NVARCHAR(255) NULL,
        province_code NVARCHAR(20) NULL,
        administrative_unit_id INT NULL
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'provinces_administrative_unit_id_fkey')
BEGIN
    ALTER TABLE dbo.provinces
    ADD CONSTRAINT provinces_administrative_unit_id_fkey
        FOREIGN KEY (administrative_unit_id) REFERENCES dbo.administrative_units(id);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'wards_administrative_unit_id_fkey')
BEGIN
    ALTER TABLE dbo.wards
    ADD CONSTRAINT wards_administrative_unit_id_fkey
        FOREIGN KEY (administrative_unit_id) REFERENCES dbo.administrative_units(id);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'wards_province_code_fkey')
BEGIN
    ALTER TABLE dbo.wards
    ADD CONSTRAINT wards_province_code_fkey
        FOREIGN KEY (province_code) REFERENCES dbo.provinces(code);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_provinces_unit' AND object_id = OBJECT_ID('dbo.provinces'))
BEGIN
    CREATE INDEX idx_provinces_unit ON dbo.provinces(administrative_unit_id);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_wards_province' AND object_id = OBJECT_ID('dbo.wards'))
BEGIN
    CREATE INDEX idx_wards_province ON dbo.wards(province_code);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_wards_unit' AND object_id = OBJECT_ID('dbo.wards'))
BEGIN
    CREATE INDEX idx_wards_unit ON dbo.wards(administrative_unit_id);
END
GO



IF COL_LENGTH('dbo.orders', 'province_code') IS NULL
BEGIN
    ALTER TABLE dbo.orders ADD province_code NVARCHAR(20) NULL;
END
GO

IF COL_LENGTH('dbo.orders', 'ward_code') IS NULL
BEGIN
    ALTER TABLE dbo.orders ADD ward_code NVARCHAR(20) NULL;
END
GO

IF COL_LENGTH('dbo.orders', 'delivery_lat') IS NULL
BEGIN
    ALTER TABLE dbo.orders ADD delivery_lat FLOAT NULL;
END
GO

IF COL_LENGTH('dbo.orders', 'delivery_lng') IS NULL
BEGIN
    ALTER TABLE dbo.orders ADD delivery_lng FLOAT NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'orders_province_code_fkey')
BEGIN
    ALTER TABLE dbo.orders
    ADD CONSTRAINT orders_province_code_fkey
        FOREIGN KEY (province_code) REFERENCES dbo.provinces(code);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'orders_ward_code_fkey')
BEGIN
    ALTER TABLE dbo.orders
    ADD CONSTRAINT orders_ward_code_fkey
        FOREIGN KEY (ward_code) REFERENCES dbo.wards(code);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_orders_province_code' AND object_id = OBJECT_ID('dbo.orders'))
BEGIN
    CREATE INDEX idx_orders_province_code ON dbo.orders(province_code);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_orders_ward_code' AND object_id = OBJECT_ID('dbo.orders'))
BEGIN
    CREATE INDEX idx_orders_ward_code ON dbo.orders(ward_code);
END
GO

-- Import
:r .\mssql_ImportData_vn_units.sql
GO
