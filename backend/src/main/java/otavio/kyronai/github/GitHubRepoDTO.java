package otavio.kyronai.github;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GitHubRepoDTO {

    private UUID   id;
    private String fullName;
    private String owner;
    private String repoName;
    private String description;
    private String branch;
    private boolean isPrivate;
    private GitHubRepo.IndexStatus indexStatus;
    private Integer indexedFilesCount;
    private LocalDateTime lastIndexedAt;
    private UUID   projectId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GitHubRepoDTO fromEntity(GitHubRepo r) {
        if (r == null) return null;
        return GitHubRepoDTO.builder()
                .id(r.getId())
                .fullName(r.getFullName())
                .owner(r.getOwner())
                .repoName(r.getRepoName())
                .description(r.getDescription())
                .branch(r.getBranch())
                .isPrivate(r.isPrivate())
                .indexStatus(r.getIndexStatus())
                .indexedFilesCount(r.getIndexedFilesCount())
                .lastIndexedAt(r.getLastIndexedAt())
                .projectId(r.getProject() != null ? r.getProject().getId() : null)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}