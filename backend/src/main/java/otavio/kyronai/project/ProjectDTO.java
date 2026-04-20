package otavio.kyronai.project;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectDTO {

    private UUID id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProjectFileDTO> files;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ProjectFileDTO {
        private UUID id;
        private String filename;
        private String fileType;
        private LocalDateTime createdAt;
        private int contentLength;
    }

    public static ProjectDTO fromEntity(Project p) {
        return ProjectDTO.builder()
                .id(p.getId()).name(p.getName())
                .description(p.getDescription())
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt())
                .build();
    }

    public static ProjectDTO fromEntityWithFiles(Project p) {
        List<ProjectFileDTO> files = p.getFiles().stream()
                .map(f -> ProjectFileDTO.builder()
                        .id(f.getId()).filename(f.getFilename())
                        .fileType(f.getFileType()).createdAt(f.getCreatedAt())
                        .contentLength(f.getContent() != null ? f.getContent().length() : 0)
                        .build())
                .toList();
        return ProjectDTO.builder()
                .id(p.getId()).name(p.getName())
                .description(p.getDescription())
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt())
                .files(files).build();
    }
}