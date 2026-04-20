package otavio.kyronai.agent;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentActionDTO {

    private UUID id;
    private UUID conversationId;
    private String actionType;
    private String description;
    private String filePath;
    private String proposedContent;
    private String status;
    private Integer executionOrder;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;

    public static AgentActionDTO fromEntity(AgentAction a) {
        if (a == null) return null;
        return AgentActionDTO.builder()
                .id(a.getId())
                .conversationId(a.getConversation().getId())
                .actionType(a.getActionType() != null ? a.getActionType().name() : null)
                .description(a.getDescription())
                .filePath(a.getFilePath())
                .proposedContent(a.getProposedContent())
                .status(a.getStatus() != null ? a.getStatus().name() : null)
                .executionOrder(a.getExecutionOrder())
                .resolvedAt(a.getResolvedAt())
                .createdAt(a.getCreatedAt())
                .build();
    }
}