package otavio.kyronai.chat;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationDTO {

    private UUID id;
    private String title;
    private String modelName;
    private boolean pinned;
    private UUID projectId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MessageDTO> messages;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MessageDTO {
        private UUID id;
        private String role;
        private String content;
        private Boolean thinkingEnabled;
        private LocalDateTime createdAt;
    }

    public static ConversationDTO fromEntity(Conversation c) {
        return ConversationDTO.builder()
                .id(c.getId())
                .title(c.getTitle())
                .modelName(c.getModelName())
                .pinned(c.isPinned())
                .projectId(c.getProjectId())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    public static ConversationDTO fromEntityWithMessages(Conversation c) {
        List<MessageDTO> msgs = c.getMessages().stream()
                .map(m -> MessageDTO.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .thinkingEnabled(m.isThinkingEnabled())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();

        return ConversationDTO.builder()
                .id(c.getId())
                .title(c.getTitle())
                .modelName(c.getModelName())
                .pinned(c.isPinned())
                .projectId(c.getProjectId())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .messages(msgs)
                .build();
    }
}