package io.veridraw.drawcore.event;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrawCompletedEvent {
    private UUID drawId;
    private String winnerEmail;
    private String winnerName;
    private String drawName;
}
