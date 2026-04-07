package otavio.openchat.model;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO para serialização de Projetos — evita LazyInitializationException
 * e garante que o conteúdo dos arquivos não seja retornado na listagem da sidebar.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectDTO {

    private UUID id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProjectFileDTO> files;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProjectFileDTO {
        private UUID id;
        private String filename;
        private String fileType;
        private LocalDateTime createdAt;
        // ⚠️ content é omitido propositalmente na listagem para não sobrecarregar
        // o payload. Só é retornado quando necessário (buildProjectContext).
        private int contentLength; // tamanho em chars para exibir "X KB" na UI
    }

    // --- Conversores estáticos ---

    public static ProjectDTO fromEntity(Project p) {
        return ProjectDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build(); // sem arquivos — para listagem da sidebar
    }

    public static ProjectDTO fromEntityWithFiles(Project p) {
        List<ProjectFileDTO> files = p.getFiles().stream()
                .map(f -> ProjectFileDTO.builder()
                        .id(f.getId())
                        .filename(f.getFilename())
                        .fileType(f.getFileType())
                        .createdAt(f.getCreatedAt())
                        .contentLength(f.getContent() != null ? f.getContent().length() : 0)
                        .build())
                .toList();

        return ProjectDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .files(files)
                .build();
    }
}