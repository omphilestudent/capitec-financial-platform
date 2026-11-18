-- Local copy of the customers this service has been told about through
-- customer.events. Lets account-service check a customer without calling
-- customer-service, and keeps it working when that service is down.
CREATE TABLE customer_refs (
                               customer_id UUID PRIMARY KEY,
                               first_name VARCHAR(100) NOT NULL,
                               last_name VARCHAR(100) NOT NULL,
                               email VARCHAR(255) NOT NULL,
                               client_type VARCHAR(30) NOT NULL,
                               received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
