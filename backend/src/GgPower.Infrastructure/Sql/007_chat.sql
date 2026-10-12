-- Chat assistant: one conversation per user and project; every model turn is kept as it was produced (blocks) so that
-- the next question replays the history unchanged.
CREATE TABLE chat_conversations (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users ON DELETE CASCADE,
    project_id uuid        NOT NULL REFERENCES projects ON DELETE CASCADE,
    title      text        NOT NULL,
    questions  int         NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX chat_conversations_user_ix ON chat_conversations (user_id, project_id, updated_at DESC);

CREATE TABLE chat_messages (
    conversation_id uuid        NOT NULL REFERENCES chat_conversations ON DELETE CASCADE,
    seq             int         NOT NULL,
    role            text        NOT NULL,      -- user | assistant
    blocks          jsonb       NOT NULL,      -- text, thinking, tool_use, tool_result… as sent to / received from the model
    shown_text      text,                      -- the question or the final answer shown in the app; null for tool rounds
    model           text,
    input_tokens    int,
    output_tokens   int,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, seq)
);
CREATE INDEX chat_messages_questions_ix ON chat_messages (created_at) WHERE role = 'user' AND shown_text IS NOT NULL;
