ALTER TABLE document_chunk
    ADD COLUMN IF NOT EXISTS embedding_vector JSONB;
