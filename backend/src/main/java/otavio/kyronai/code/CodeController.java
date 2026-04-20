package otavio.kyronai.code;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
public class CodeController {

    private final CodeGenerationService codeGenerationService;

    @GetMapping("/session/{conversationId}")
    public ResponseEntity<CodeSessionDTO> getSession(@PathVariable UUID conversationId) {
        return codeGenerationService.getSessionByConversation(conversationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/session/{sessionId}/file/{fileId}")
    public ResponseEntity<CodeSessionDTO.GeneratedFileDTO> getFile(
            @PathVariable UUID sessionId, @PathVariable UUID fileId) {
        return codeGenerationService.getSessionById(sessionId)
                .flatMap(s -> s.getFiles().stream()
                        .filter(f -> f.getId().equals(fileId)).findFirst())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/session/{conversationId}/download/file/{fileId}")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable UUID conversationId, @PathVariable UUID fileId) {
        return codeGenerationService.getSessionByConversation(conversationId)
                .flatMap(s -> s.getFiles().stream()
                        .filter(f -> f.getId().equals(fileId)).findFirst())
                .map(f -> {
                    byte[] bytes = f.getContent().getBytes(StandardCharsets.UTF_8);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + f.getFileName() + "\"")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(bytes);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/session/{conversationId}/download/zip")
    public ResponseEntity<byte[]> downloadZip(@PathVariable UUID conversationId) {
        return codeGenerationService.getSessionByConversation(conversationId)
                .map(session -> {
                    try {
                        String projectName = session.getTitle() != null
                                ? session.getTitle().replaceAll("[^a-zA-Z0-9_-]", "_")
                                : "kyron-project";
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                            for (var file : session.getFiles()) {
                                zos.putNextEntry(new ZipEntry(projectName + "/" + file.getFilePath()));
                                zos.write(file.getContent().getBytes(StandardCharsets.UTF_8));
                                zos.closeEntry();
                            }
                        }
                        byte[] zip = baos.toByteArray();
                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + projectName + ".zip\"")
                                .contentType(MediaType.parseMediaType("application/zip"))
                                .body(zip);
                    } catch (Exception e) {
                        log.error("Erro ao gerar ZIP", e);
                        return ResponseEntity.internalServerError().<byte[]>build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/session/{conversationId}")
    public ResponseEntity<Void> clearSession(@PathVariable UUID conversationId) {
        return codeGenerationService.getSessionByConversation(conversationId)
                .map(s -> {
                    codeGenerationService.clearSession(s.getId());
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/system-prompt")
    public ResponseEntity<Map<String, String>> systemPrompt(
            @RequestParam(required = false) String language) {
        return ResponseEntity.ok(Map.of("systemPrompt",
                codeGenerationService.buildCodeModeSystemPrompt(language)));
    }
}