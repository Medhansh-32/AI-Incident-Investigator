-- Enables the pgvector extension. Spring Data JPA (ddl-auto: update) creates
-- the actual tables on first boot; this just makes the VECTOR type available.
CREATE EXTENSION IF NOT EXISTS vector;
