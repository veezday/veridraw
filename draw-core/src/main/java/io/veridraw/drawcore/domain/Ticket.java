package io.veridraw.drawcore.domain;

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
@Table("tickets")
public class Ticket {
    @Id
    private UUID id;

    private UUID drawId;
    private String participantName;
    private String participantEmail;

    private String status;
    private Instant purchasedAt;
}
