package otavio.kyronai.code;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CodeSessionDTO {

    private UUID id;
    private UUID conversationId;
    private String title;
    private String primaryLanguage;
    private String status;
    private String summary;
    private LocalDateTime startedAt;
    private LocalDateTime lastActivityAt;
    private List<GeneratedFileDTO> files;

    public static CodeSessionDTO fromEntity(CodeSession s) {
        List<GeneratedFileDTO> files = s.getFiles() == null ? List.of()
                : s.getFiles().stream().map(GeneratedFileDTO::fromEntity)
                   .collect(Collectors.toList());
        return CodeSessionDTO.builder()
                .id(s.getId())
                .conversationId(s.getConversation().getId())
                .title(s.getTitle())
                .primaryLanguage(s.getPrimaryLanguage())
                .status(s.getStatus())
                .summary(s.getSummary())
                .startedAt(s.getCreatedAt())
                .lastActivityAt(s.getUpdatedAt())
                .files(files)
                .build();
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GeneratedFileDTO {
        private UUID id;
        private String filePath;
        private String fileName;
        private String extension;
        private String content;
        private String previousContent;
        private Integer version;
        private boolean newFile;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static GeneratedFileDTO fromEntity(GeneratedFile f) {
            return GeneratedFileDTO.builder()
                    .id(f.getId())
                    .filePath(f.getFilePath())
                    .fileName(f.getFileName())
                    .extension(f.getExtension())
                    .content(f.getContent())
                    .previousContent(f.getPreviousContent())
                    .version(f.getVersion())
                    .newFile(f.isNewFile())
                    .createdAt(f.getCreatedAt())
                    .updatedAt(f.getUpdatedAt())
                    .build();
        }
    }
}