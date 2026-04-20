package otavio.kyronai.agent;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import otavio.kyronai.chat.Conversation;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_actions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private ActionType actionType;

    @Column(length = 1000)
    private String description;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "proposed_content", columnDefinition = "TEXT")
    private String proposedContent;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    @Builder.Default
    private ActionStatus status = ActionStatus.PENDING;

    @Column(name = "execution_order")
    private Integer executionOrder;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ActionType  { CREATE_FILE, EDIT_FILE, DELETE_FILE, RUN_COMMAND, EXPLAIN }
    public enum ActionStatus { PENDING, APPROVED, REJECTED, EXECUTED, FAILED }
}