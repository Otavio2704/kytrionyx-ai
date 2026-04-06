package otavio.openchat.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(length = 255)
    private String title;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Se true, conversa aparece na seção 'Fixados' acima dos recentes */
    @Column(nullable = false)
    @Builder.Default
    private boolean pinned = false;

    /**
     * Relacionamento 1:N com mensagens.
     * CascadeType.ALL + orphanRemoval garante que deletar a conversa
     * apaga todas as mensagens associadas automaticamente.
     * FetchType.LAZY evita carregar mensagens desnecessariamente
     * na listagem do histórico (sidebar).
     */
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("created_at ASC")
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    // --- Métodos auxiliares ---

    public void addMessage(Message message) {
        messages.add(message);
        message.setConversation(this);
    }

    public void removeMessage(Message message) {
        messages.remove(message);
        message.setConversation(null);
    }
}