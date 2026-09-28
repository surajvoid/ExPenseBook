-- =========================================================
-- ExPense Book Database Schema (MySQL 8.0+)
-- Database: expensebook
-- =========================================================

CREATE DATABASE IF NOT EXISTS expensebook CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE expensebook;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'USER',
    financial_mode VARCHAR(30) DEFAULT 'TRACK_ONLY',
    currency VARCHAR(10) DEFAULT '₹',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Categories Table (System defaults have user_id = NULL)
CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NULL,
    name VARCHAR(50) NOT NULL,
    icon_name VARCHAR(50) NOT NULL,
    color VARCHAR(20) DEFAULT '#78BFA0',
    is_default BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cat_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 3. Expenses Table
CREATE TABLE IF NOT EXISTS expenses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    category_id INT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    payment_mode VARCHAR(30) NOT NULL,
    expense_date DATE NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_exp_cat FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
);

-- 4. Income Table (Optional Income Tracking)
CREATE TABLE IF NOT EXISTS income (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    source VARCHAR(50) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    income_date DATE NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inc_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 5. Budgets Table (For Track + Budget Mode)
CREATE TABLE IF NOT EXISTS budgets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    month INT NOT NULL,
    year INT NOT NULL,
    total_budget DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_month_year (user_id, month, year),
    CONSTRAINT fk_bgt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 6. Category Budgets Table (Category-specific spending limits)
CREATE TABLE IF NOT EXISTS category_budgets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    budget_id INT NOT NULL,
    category_id INT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    UNIQUE KEY uq_budget_cat (budget_id, category_id),
    CONSTRAINT fk_cb_budget FOREIGN KEY (budget_id) REFERENCES budgets(id) ON DELETE CASCADE,
    CONSTRAINT fk_cb_cat FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
);

-- 7. Recurring Expenses Table
CREATE TABLE IF NOT EXISTS recurring_expenses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    title VARCHAR(100) NOT NULL,
    category_id INT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    frequency VARCHAR(30) DEFAULT 'MONTHLY',
    payment_mode VARCHAR(30) NOT NULL,
    next_due_date DATE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rec_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_rec_cat FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
);

-- Seed Default Categories
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Food', 'FOOD', '#E88C8C', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Food' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Groceries', 'GROCERIES', '#78BFA0', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Groceries' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Travel', 'TRAVEL', '#A9CFE0', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Travel' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Shopping', 'SHOPPING', '#F5C6A5', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Shopping' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Education', 'EDUCATION', '#C9B9E8', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Education' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Health', 'HEALTH', '#E88C8C', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Health' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Entertainment', 'ENTERTAINMENT', '#F5C6A5', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Entertainment' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Bills', 'BILLS', '#71807A', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Bills' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Recharge', 'RECHARGE', '#78BFA0', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Recharge' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Rent', 'RENT', '#A9CFE0', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Rent' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Fitness', 'FITNESS', '#78BFA0', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Fitness' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Personal Care', 'PERSONAL_CARE', '#F5C6A5', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Personal Care' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Subscription', 'SUBSCRIPTION', '#C9B9E8', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Subscription' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Electronics', 'ELECTRONICS', '#A9CFE0', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Electronics' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Gifts', 'GIFTS', '#F5C6A5', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Gifts' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Others', 'OTHERS', '#71807A', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Others' AND user_id IS NULL);
INSERT INTO categories (user_id, name, icon_name, color, is_default)
SELECT NULL, 'Savings', 'SAVINGS', '#10B981', TRUE WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Savings' AND user_id IS NULL);
