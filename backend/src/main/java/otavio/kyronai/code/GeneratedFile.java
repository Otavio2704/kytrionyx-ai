package otavio.kyronai.code;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "generated_files")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GeneratedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_session_id", nullable = false)
    private CodeSession codeSession;

    /** Caminho relativo à raiz do projeto: ex "src/main/java/App.java" */
    @Column(name = "file_path", length = 500, nullable = false)
    private String filePath;

    /** Apenas o nome do arquivo: ex "App.java" */
    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    /** Extensão sem ponto: ex "java" */
    @Column(length = 50)
    private String extension;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "previous_content", columnDefinition = "TEXT")
    private String previousContent;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "is_new_file", nullable = false)
    @Builder.Default
    private boolean newFile = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}