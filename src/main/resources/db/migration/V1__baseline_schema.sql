CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    description TEXT,
    slug VARCHAR(140) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_category_name UNIQUE (name),
    CONSTRAINT uq_category_slug UNIQUE (slug)
);

CREATE TABLE department (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    code VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_department_name UNIQUE (name),
    CONSTRAINT uq_department_code UNIQUE (code)
);

CREATE TABLE app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entra_object_id VARCHAR(128) NOT NULL,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    department VARCHAR(120),
    roles JSONB NOT NULL DEFAULT '[]'::jsonb,
    last_login TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notification_preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_app_user_entra_object_id UNIQUE (entra_object_id),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE TABLE document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    category_id UUID NOT NULL REFERENCES category (id),
    department_id UUID NOT NULL REFERENCES department (id),
    status VARCHAR(32) NOT NULL,
    current_version INTEGER NOT NULL DEFAULT 1,
    uploaded_by VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    CONSTRAINT uq_document_slug UNIQUE (slug),
    CONSTRAINT ck_document_status CHECK (status IN ('DRAFT', 'UNDER_REVIEW', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_document_current_version_positive CHECK (current_version > 0)
);

CREATE TABLE document_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    blob_path TEXT NOT NULL,
    blob_version_id TEXT,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    effective_date DATE,
    change_summary TEXT,
    uploaded_by VARCHAR(320) NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    processing_error TEXT,
    total_chunks INTEGER NOT NULL DEFAULT 0,
    total_pages INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_version_number UNIQUE (document_id, version_number),
    CONSTRAINT ck_document_version_file_type CHECK (file_type IN ('PDF', 'DOCX')),
    CONSTRAINT ck_document_version_processing_status CHECK (
        processing_status IN ('UPLOADED', 'PARSING', 'CHUNKING', 'EMBEDDING', 'INDEXING', 'READY', 'FAILED')
    ),
    CONSTRAINT ck_document_version_file_size_non_negative CHECK (file_size_bytes >= 0),
    CONSTRAINT ck_document_version_total_chunks_non_negative CHECK (total_chunks >= 0),
    CONSTRAINT ck_document_version_total_pages_non_negative CHECK (total_pages >= 0)
);

CREATE TABLE document_chunk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_version_id UUID NOT NULL REFERENCES document_version (id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    page_number INTEGER,
    section_heading TEXT,
    start_char_offset INTEGER,
    end_char_offset INTEGER,
    ai_search_doc_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_chunk_version_index UNIQUE (document_version_id, chunk_index),
    CONSTRAINT uq_document_chunk_ai_search_doc_id UNIQUE (ai_search_doc_id),
    CONSTRAINT ck_document_chunk_index_non_negative CHECK (chunk_index >= 0),
    CONSTRAINT ck_document_chunk_page_number_positive CHECK (page_number IS NULL OR page_number > 0),
    CONSTRAINT ck_document_chunk_offsets_valid CHECK (
        start_char_offset IS NULL
        OR end_char_offset IS NULL
        OR start_char_offset <= end_char_offset
    )
);

CREATE TABLE document_access_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    access_type VARCHAR(32) NOT NULL,
    access_value VARCHAR(255) NOT NULL,
    granted_by VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_document_access_policy_type CHECK (access_type IN ('ROLE', 'DEPARTMENT', 'USER', 'ALL')),
    CONSTRAINT uq_document_access_policy UNIQUE (document_id, access_type, access_value)
);

CREATE TABLE chat_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    message_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_chat_session_message_count_non_negative CHECK (message_count >= 0)
);

CREATE TABLE chat_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES chat_session (id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    confidence_score DOUBLE PRECISION,
    token_count_prompt INTEGER NOT NULL DEFAULT 0,
    token_count_completion INTEGER NOT NULL DEFAULT 0,
    retrieval_count INTEGER NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_chat_message_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    CONSTRAINT ck_chat_message_confidence_score CHECK (
        confidence_score IS NULL OR (confidence_score >= 0.0 AND confidence_score <= 1.0)
    ),
    CONSTRAINT ck_chat_message_token_count_prompt_non_negative CHECK (token_count_prompt >= 0),
    CONSTRAINT ck_chat_message_token_count_completion_non_negative CHECK (token_count_completion >= 0),
    CONSTRAINT ck_chat_message_retrieval_count_non_negative CHECK (retrieval_count >= 0),
    CONSTRAINT ck_chat_message_latency_non_negative CHECK (latency_ms >= 0)
);

CREATE TABLE message_source (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES chat_message (id) ON DELETE CASCADE,
    chunk_id UUID NOT NULL REFERENCES document_chunk (id),
    document_id UUID NOT NULL REFERENCES document (id),
    document_title VARCHAR(255) NOT NULL,
    page_number INTEGER,
    section_heading TEXT,
    excerpt TEXT,
    relevance_score DOUBLE PRECISION,
    display_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_message_source_page_number_positive CHECK (page_number IS NULL OR page_number > 0),
    CONSTRAINT ck_message_source_display_order_non_negative CHECK (display_order >= 0)
);

CREATE TABLE user_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    message_id UUID NOT NULL REFERENCES chat_message (id) ON DELETE CASCADE,
    rating VARCHAR(32) NOT NULL,
    comment TEXT,
    flagged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_feedback_rating CHECK (rating IN ('THUMBS_UP', 'THUMBS_DOWN')),
    CONSTRAINT uq_user_feedback_user_message UNIQUE (user_id, message_id)
);

CREATE TABLE query_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES app_user (id) ON DELETE SET NULL,
    session_id UUID REFERENCES chat_session (id) ON DELETE SET NULL,
    message_id UUID REFERENCES chat_message (id) ON DELETE SET NULL,
    action VARCHAR(32) NOT NULL,
    query_text TEXT,
    documents_accessed JSONB NOT NULL DEFAULT '[]'::jsonb,
    ip_address VARCHAR(64),
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_query_audit_log_action CHECK (
        action IN ('QUERY', 'SEARCH', 'DOC_VIEW', 'DOC_UPLOAD', 'ACL_CHANGE', 'LOGIN')
    )
);

CREATE TABLE notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    channel VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMPTZ,
    CONSTRAINT ck_notification_type CHECK (type IN ('POLICY_UPDATED', 'POLICY_PUBLISHED', 'POLICY_RETIRED')),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('IN_APP', 'EMAIL')),
    CONSTRAINT ck_notification_read_timestamp CHECK (read = FALSE OR read_at IS NOT NULL)
);

CREATE INDEX idx_document_status ON document (status);
CREATE INDEX idx_document_category ON document (category_id);
CREATE INDEX idx_document_department ON document (department_id);

CREATE INDEX idx_version_document ON document_version (document_id, version_number);
CREATE INDEX idx_version_status ON document_version (processing_status);

CREATE INDEX idx_chunk_version ON document_chunk (document_version_id);
CREATE INDEX idx_chunk_document ON document_chunk (document_id);

CREATE INDEX idx_session_user ON chat_session (user_id, last_message_at DESC);
CREATE INDEX idx_message_session ON chat_message (session_id, created_at);

CREATE INDEX idx_audit_user ON query_audit_log (user_id, created_at DESC);
CREATE INDEX idx_audit_action ON query_audit_log (action, created_at DESC);

CREATE INDEX idx_feedback_rating ON user_feedback (rating, created_at DESC);
CREATE INDEX idx_feedback_flagged ON user_feedback (flagged) WHERE flagged = TRUE;

CREATE INDEX idx_access_document ON document_access_policy (document_id);
CREATE INDEX idx_access_type_value ON document_access_policy (access_type, access_value);

CREATE INDEX idx_notification_user ON notification (user_id, read, created_at DESC);
