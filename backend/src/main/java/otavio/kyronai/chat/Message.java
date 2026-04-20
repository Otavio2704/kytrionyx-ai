package otavio.kyronai.chat;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(length = 10, nullable = false)
    private String role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "thinking_enabled")
    @Builder.Default
    private Boolean thinkingEnabled = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Message(Conversation conversation, String role, String content, Boolean thinkingEnabled) {
        this.conversation    = conversation;
        this.role            = role;
        this.content         = content;
        this.thinkingEnabled = thinkingEnabled != null ? thinkingEnabled : false;
    }

    public boolean isThinkingEnabled() {
        return Boolean.TRUE.equals(thinkingEnabled);
    }

    public Map<String, String> toOllamaMap() {
        return Map.of("role", role, "content", content);
    }
}