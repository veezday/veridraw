package io.veridraw.drawcore.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "draws")
public class Draw {
    @Id
    private UUID id;

    private String name;
    private String description;
    private Integer maxTickets;
    private BigDecimal ticketPrice;

    private DrawStatus status;
    private UUID winnerId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
}
