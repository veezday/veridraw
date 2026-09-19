-- Таблица розыгрышей
CREATE TABLE IF NOT EXISTS draws (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     name VARCHAR(255) NOT NULL,
                                     description TEXT,
                                     status VARCHAR(50) NOT NULL DEFAULT 'CREATED',
                                     max_tickets INTEGER NOT NULL DEFAULT 100,
                                     ticket_price DECIMAL(10, 2) NOT NULL DEFAULT 10.00,
                                     winner_id UUID,
                                     created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                     completed_at TIMESTAMP WITH TIME ZONE
);

-- Таблица билетов
CREATE TABLE IF NOT EXISTS tickets (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       draw_id UUID NOT NULL REFERENCES draws(id) ON DELETE CASCADE,
                                       participant_email VARCHAR(255) NOT NULL,
                                       participant_name VARCHAR(255),
                                       status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                                       purchased_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                       UNIQUE(draw_id, participant_email)
);

-- Outbox таблица для надёжной публикации событий
CREATE TABLE IF NOT EXISTS outbox_events (
                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                             aggregate_type VARCHAR(100) NOT NULL,
                                             aggregate_id UUID NOT NULL,
                                             event_type VARCHAR(100) NOT NULL,
                                             payload JSONB NOT NULL,
                                             created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                             published BOOLEAN DEFAULT FALSE,
                                             published_at TIMESTAMP WITH TIME ZONE
);

-- Индексы
CREATE INDEX IF NOT EXISTS idx_tickets_draw_id ON tickets(draw_id);
CREATE INDEX IF NOT EXISTS idx_tickets_email ON tickets(participant_email);
CREATE INDEX IF NOT EXISTS idx_outbox_published ON outbox_events(published);
CREATE INDEX IF NOT EXISTS idx_draws_status ON draws(status);