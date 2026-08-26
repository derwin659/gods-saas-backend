CREATE TABLE IF NOT EXISTS featured_customer (
 featured_customer_id BIGSERIAL PRIMARY KEY,
 business_name VARCHAR(150) NOT NULL,
 business_type VARCHAR(60),
 city VARCHAR(100),
 logo_url VARCHAR(500) NOT NULL,
 logo_public_id VARCHAR(300) NOT NULL,
 website VARCHAR(300),
 testimonial VARCHAR(350),
 visible BOOLEAN NOT NULL DEFAULT TRUE,
 sort_order INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_featured_customer_public ON featured_customer (visible, sort_order, featured_customer_id);