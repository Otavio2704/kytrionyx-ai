package otavio.kyronai.github;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import otavio.kyronai.project.Project;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "github_repositories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GitHubRepo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** "owner/repo" — identificador único */
    @Column(name = "full_name", nullable = false, length = 300, unique = true)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String owner;

    @Column(name = "repo_name", nullable = false, length = 150)
    private String repoName;

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String branch;

    @Column(name = "access_token", length = 500)
    private String accessToken;

    @Column(name = "is_private")
    @Builder.Default
    private boolean isPrivate = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_status", length = 50)
    @Builder.Default
    private IndexStatus indexStatus = IndexStatus.PENDING;

    @Column(name = "indexed_files_count")
    private Integer indexedFilesCount;

    @Column(name = "last_indexed_at")
    private LocalDateTime lastIndexedAt;

    @Column(name = "context_index", columnDefinition = "TEXT")
    private String contextIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum IndexStatus { PENDING, INDEXING, READY, ERROR }
}