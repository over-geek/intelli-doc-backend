CREATE TABLE saved_answer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    message_id UUID NOT NULL REFERENCES chat_message (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_saved_answer_user_message UNIQUE (user_id, message_id)
);

CREATE INDEX idx_saved_answer_user_created_at ON saved_answer (user_id, created_at DESC);
CREATE INDEX idx_saved_answer_message ON saved_answer (message_id);
