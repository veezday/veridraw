# Оглавление

- [Описание проекта](./../README.md)
- [Описание сервисов](./info.md)

### Архитектура приложения
*Общая схема взаимодействия микросервисов, внешней инфраструктуры и стека наблюдаемости. Отображает используемые порты, базы данных и основные потоки данных (бизнес-логика, метрики, логи).*

```mermaid
graph TB
    subgraph Services["Микросервисные сервисы"]
        Keycloak["keycloak:8080"]
        Gateway["gateway:8081"]
        DrawCore["draw-core:8082"]
        NotifyWorker["notify-worker:8083"]
    end

    subgraph Infrastructure["Инфраструктура"]
        PostgresDrawCore[("postgres:5432<br/>db:draw-core")]
        PostgresNotifyWorker[("postgres:5432<br/>db:notify-worker")]
        Redis[("redis:6379")]
        Kafka["kafka:9092"]
        Mailhog["mailhog:1025,8025"]
    end

    subgraph Observability["Observability стек"]
        OtelCollector["otel-collector<br/>8888,4317,4318"]
        Prometheus["prometheus:9090"]
        Jaeger["jaeger:16686,14250"]
        Loki["loki:3100"]
        Grafana["grafana:3000"]
        Promtail["promtail"]
        Cadviser["cAdvisor:8025"]
    end

    subgraph ExternalServices["Внешние сервисы"]
        RandomOrg["randomOrg API"]
    end

    %% Requests
    Gateway -->|token validation| Keycloak
    DrawCore -->|public keys| Keycloak
    NotifyWorker -->|public keys| Keycloak

    %% Rate limiting & storage
    Gateway -->|rate limits| Redis

    %% Data storage
    DrawCore -->|draws, tickets, outbox| PostgresDrawCore
    NotifyWorker -->|inbox events| PostgresNotifyWorker

    %% Event streaming
    DrawCore -->|draw-events| Kafka
    NotifyWorker -->|subscribes draw-events| Kafka
    NotifyWorker -->|send email| Mailhog

    %% External API
    DrawCore -->|random selection| RandomOrg

    %% Metrics: JVM + Custom
    Gateway -->|metrics via javaagent| OtelCollector
    DrawCore -->|metrics via javaagent| OtelCollector
    NotifyWorker -->|metrics via javaagent| OtelCollector

    %% Metrics: Services
    PostgresDrawCore -->|metrics| OtelCollector
    PostgresNotifyWorker -->|metrics| OtelCollector
    Redis -->|metrics| OtelCollector
    Kafka -->|metrics| OtelCollector
    Keycloak -->|metrics| OtelCollector

    %% Container metrics
    Cadviser -->|container metrics| OtelCollector

    %% Metrics to storage
    OtelCollector -->|store metrics| Prometheus

    %% Traces
    Gateway -->|traces| OtelCollector
    DrawCore -->|traces| OtelCollector
    NotifyWorker -->|traces| OtelCollector
    OtelCollector -->|store traces| Jaeger

    %% Logs
    Promtail -->|collect logs| Loki

    %% Dashboards
    Grafana -->|metrics| Prometheus
    Grafana -->|traces| Jaeger
    Grafana -->|logs| Loki

    classDef service stroke:#818cf8,fill:#696969
    classDef database stroke:#4ade80,fill:#696969
    classDef queue stroke:#fb923c,fill:#696969
    classDef observability stroke:#a78bfa,fill:#696969
    classDef external stroke:#f87171,fill:#696969
    classDef keycloak stroke:#22d3ee,fill:#696969

    class Gateway,DrawCore,NotifyWorker service
    class PostgresDrawCore,PostgresNotifyWorker,Redis database
    class Kafka,Mailhog queue
    class OtelCollector,Prometheus,Jaeger,Loki,Grafana,Promtail,Cadviser observability
    class RandomOrg external
    class Keycloak keycloak
```

### Observability
*Упрощенная схема пайплайнов сбора данных. Наглядно показывает три главных потока: откуда (Sources), через какие агенты (Agents) и в какие хранилища (Storage) попадают метрики, логи и трейсы.*

```mermaid
flowchart LR
    subgraph Sources [Источники данных]
        Apps[Gateway\nDraw-Core\nNotify-Worker]
        Infra[PostgreSQL\nRedis\nKafka\nKeycloak]
        Docker[Docker Containers]
    end

    subgraph Agents [Агенты и Сборщики]
        JavaAgent[Java Agent]
        Otel[OTel Collector]
        CAdvisor[cAdvisor]
        Promtail[Promtail]
    end

    subgraph Storage [Хранилища]
        Prom[(Prometheus)]
        Loki[(Loki)]
        Jaeger[(Jaeger)]
    end

    Apps -- JVM --> JavaAgent --> Otel
    Apps -- Custom --> Otel
    Infra --> Otel
    Docker -- Metrics --> CAdvisor --> Otel
    Docker -- Logs --> Promtail

    Otel --> Prom
    Otel --> Jaeger
    Promtail --> Loki
    
    classDef source fill:#f9f9f9,stroke:#333,stroke-width:1px;
    classDef agent fill:#e1f5fe,stroke:#0288d1,stroke-width:1px;
    classDef storage fill:#e8f5e9,stroke:#388e3c,stroke-width:2px;
    class Sources source;
    class Agents agent;
    class Storage storage;
```

### Последовательность завершения розыгрыша (Outbox / Inbox Pattern)
*Детальный процесс завершения розыгрыша. Демонстрирует гарантированную доставку события о победителе через паттерн Outbox (в Draw-Core) и надежную обработку с повторными попытками отправки email через паттерн Inbox (в Notify-Worker).*

```mermaid
sequenceDiagram
    actor User
    participant GW as Gateway :8081
    participant DC as Draw-Core :8082
    participant DB1 as PostgreSQL (draw-core)
    participant RO as Random.org API
    participant KF as Kafka (draw-events)
    participant NW as Notify-Worker :8083
    participant DB2 as PostgreSQL (notify-worker)
    participant MH as MailHog

    User->>GW: POST /api/draws/{id}/complete
    GW->>DC: Forward с заголовками X-User-*
    
    DC->>RO: Запрос случайного индекса
    RO-->>DC: Случайный индекс (победитель)
    
    rect rgb(105, 105, 106)
        Note over DC, DB1: Транзакция
        DC->>DB1: UPDATE status = COMPLETED
        DC->>DB1: INSERT outbox_event (published=false)
    end
    
    DC-->>GW: 200 OK
    GW-->>User: 200 OK

    rect rgb(105, 105, 106)
        Note over DC, KF: Outbox Poller (каждые 5 сек)
        DC->>DB1: SELECT * WHERE published=false
        DC->>KF: Publish DrawCompleted Event (JSON)
        DC->>DB1: UPDATE published=true
    end

    rect rgb(105, 105, 105)
        Note over KF, DB2: Inbox Processing
        KF->>NW: Consume DrawCompleted Event
        NW->>DB2: INSERT inbox_event (published=false)
        NW->>KF: Acknowledge message
        
        Note over NW, DB2: Inbox Poller
        NW->>DB2: SELECT * WHERE published=false
        NW->>MH: emailService.send() (retry up to 3x)
        alt Успешно
            NW->>DB2: UPDATE published=true
        end
    end
```

### Конечный автомат статусов розыгрыша (State Diagram)
*Жизненный цикл сущности "Розыгрыш". Отражает строго допустимые переходы между статусами и API-эндпоинты, которые эти переходы инициируют.*

```mermaid
stateDiagram-v2
    [*] --> CREATED : POST /api/draws
    
    CREATED --> ACTIVATED : POST /api/draws/{id}/activate
    CREATED --> CANCELLED : POST /api/draws/{id}/cancell
    
    ACTIVATED --> COMPLETED : POST /api/draws/{id}/complete\n(выбор победителя, Outbox)
    ACTIVATED --> CANCELLED : POST /api/draws/{id}/cancell
    
    COMPLETED --> [*]
    CANCELLED --> [*]
    
    note right of ACTIVATED
        Разрешена покупка билетов
        POST /api/tickets
    end note
```

### Поток аутентификации и маршрутизации в Gateway
*Алгоритм обработки входящего HTTP-запроса. Включает каскадные проверки rate limiting (по IP и User ID), валидацию JWT, обогащение запроса заголовками и fallback-механизм на случай недоступности целевого микросервиса.*

```mermaid
sequenceDiagram
    actor Client
    participant GW as Gateway :8081
    participant RD as Redis :6379
    participant KC as Keycloak :8080
    participant MS as Microservice (DC/NW)

    Client->>GW: HTTP Request + Bearer Token
    
    GW->>RD: Проверка IP Rate Limiter
    alt Превышен лимит
        GW-->>Client: 429 Too Many Requests
    end
    
    GW->>GW: Валидация JWT (по публичным ключам KC)
    alt Токен невалиден
        GW-->>Client: 401 Unauthorized
    end
    
    GW->>RD: Проверка User Rate Limiter (по X-User-Id)
    alt Превышен лимит
        GW-->>Client: 429 Too Many Requests
    end
    
    GW->>GW: Добавление заголовков:\nX-User-Id, X-User-Name, X-User-Email
    GW->>MS: Роутинг запроса
    
    alt MS недоступен
        GW->>GW: Fallback Controller (forward:/fallback/...)
        GW-->>Client: 503 + Fallback Response
    else MS доступен
        MS-->>GW: 200 OK
        GW-->>Client: 200 OK
    end
```
