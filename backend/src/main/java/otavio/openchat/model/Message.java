package otavio.openchat.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * FK para a conversa pai.
     * ManyToOne com LAZY evita carregar a conversa inteira ao buscar mensagens.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /**
     * Papel do remetente: "user" ou "assistant".
     * Mapeado diretamente para o campo "role" esperado pela API do Ollama.
     */
    @Column(length = 10, nullable = false)
    private String role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // --- Construtor de conveniência (sem ID e sem data, gerados automaticamente) ---

    public Message(Conversation conversation, String role, String content) {
        this.conversation = conversation;
        this.role = role;
        this.content = content;
    }

    /**
     * Converte a mensagem para o formato Map esperado pelo payload da API do Ollama:
     * { "role": "user", "content": "..." }
     */
    public java.util.Map<String, String> toOllamaMap() {
        return java.util.Map.of(
                "role", this.role,
                "content", this.content
        );
    }
}
