package otavio.openchat.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representa um fato sobre o usuário armazenado para personalizar respostas futuras.
 * Ex: "Usuário prefere respostas em português", "Usuário trabalha com Java"
 */
@Entity
@Table(name = "memories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Memory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Conteúdo da memória — fato sobre o usuário */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** Categoria opcional: preferência, contexto, habilidade, etc. */
    @Column(length = 50)
    private String category;

    /** Se false, a memória não é injetada no contexto */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}