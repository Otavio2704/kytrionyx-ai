package otavio.openchat.controller;

import otavio.openchat.model.ProjectDTO;
import otavio.openchat.model.ProjectFile;
import otavio.openchat.service.FileExtractorService;
import otavio.openchat.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final FileExtractorService fileExtractorService;

    /** GET /api/projects — lista todos os projetos sem conteúdo dos arquivos */
    @GetMapping
    public ResponseEntity<List<ProjectDTO>> listProjects() {
        List<ProjectDTO> list = projectService.getAllProjects()
                .stream()
                .map(ProjectDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    /** GET /api/projects/{id} — retorna um projeto com metadados dos arquivos (sem conteúdo) */
    @GetMapping("/{id}")
    public ResponseEntity<ProjectDTO> getProject(@PathVariable UUID id) {
        return projectService.getProjectById(id)
                .map(ProjectDTO::fromEntityWithFiles)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ProjectDTO> createProject(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(
                ProjectDTO.fromEntity(projectService.createProject(name, body.get("description")))
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectDTO> updateProject(@PathVariable UUID id,
                                                     @RequestBody Map<String, String> body) {
        return projectService.updateProject(id, body.get("name"), body.get("description"))
                .map(ProjectDTO::fromEntityWithFiles)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        return projectService.deleteProject(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** POST /api/projects/{id}/files — faz upload e extrai texto automaticamente */
    @PostMapping("/{id}/files")
    public ResponseEntity<ProjectDTO.ProjectFileDTO> addFile(@PathVariable UUID id,
                                                              @RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "arquivo";
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : "";

        try {
            String content = fileExtractorService.extract(file, ext);
            if (content == null || content.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            ProjectFile saved = projectService.addFile(id, filename, ext, content);
            return ResponseEntity.ok(
                    ProjectDTO.ProjectFileDTO.builder()
                            .id(saved.getId())
                            .filename(saved.getFilename())
                            .fileType(saved.getFileType())
                            .createdAt(saved.getCreatedAt())
                            .contentLength(saved.getContent() != null ? saved.getContent().length() : 0)
                            .build()
            );
        } catch (Exception e) {
            log.error("Erro ao processar arquivo '{}' para projeto {}", filename, id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /** POST /api/projects/{id}/texts — adiciona texto livre como "arquivo" */
    @PostMapping("/{id}/texts")
    public ResponseEntity<ProjectDTO.ProjectFileDTO> addText(@PathVariable UUID id,
                                                              @RequestBody Map<String, String> body) {
        String name    = body.getOrDefault("name", "Texto");
        String content = body.get("content");
        if (content == null || content.isBlank()) return ResponseEntity.badRequest().build();
        ProjectFile saved = projectService.addFile(id, name, "text", content);
        return ResponseEntity.ok(
                ProjectDTO.ProjectFileDTO.builder()
                        .id(saved.getId())
                        .filename(saved.getFilename())
                        .fileType(saved.getFileType())
                        .createdAt(saved.getCreatedAt())
                        .contentLength(saved.getContent() != null ? saved.getContent().length() : 0)
                        .build()
        );
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable UUID fileId) {
        return projectService.deleteFile(fileId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}