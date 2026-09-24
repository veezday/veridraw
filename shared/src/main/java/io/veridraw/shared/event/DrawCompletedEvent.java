package io.veridraw.shared.event;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawCompletedEvent implements DomainEvent {
    UUID drawId;
    String winnerEmail;
    String winnerName;
    String drawName;
}
