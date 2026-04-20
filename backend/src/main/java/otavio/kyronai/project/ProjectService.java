package otavio.kyronai.project;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository    projectRepository;
    private final ProjectFileRepository fileRepository;

    @Transactional(readOnly = true)
    public List<Project> getAllProjects() {
        return projectRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Project> getProjectById(UUID id) {
        return projectRepository.findById(id).map(p -> {
            p.getFiles().size();
            return p;
        });
    }

    @Transactional
    public Project createProject(String name, String description) {
        return projectRepository.save(
                Project.builder().name(name).description(description).build());
    }

    @Transactional
    public Optional<Project> updateProject(UUID id, String name, String description) {
        return projectRepository.findById(id).map(p -> {
            if (name        != null && !name.isBlank()) p.setName(name);
            if (description != null) p.setDescription(description);
            return projectRepository.save(p);
        });
    }

    @Transactional
    public boolean deleteProject(UUID id) {
        if (!projectRepository.existsById(id)) return false;
        projectRepository.deleteById(id);
        return true;
    }

    @Transactional
    public ProjectFile addFile(UUID projectId, String filename,
                                String fileType, String content) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Projeto não encontrado: " + projectId));
        String truncated = content.length() > 50_000
                ? content.substring(0, 50_000) + "\n[... truncado ...]"
                : content;
        return fileRepository.save(ProjectFile.builder()
                .project(project).filename(filename)
                .fileType(fileType).content(truncated).build());
    }

    @Transactional
    public boolean deleteFile(UUID fileId) {
        if (!fileRepository.existsById(fileId)) return false;
        fileRepository.deleteById(fileId);
        return true;
    }

    @Transactional(readOnly = true)
    public String buildProjectContext(UUID projectId) {
        return projectRepository.findById(projectId).map(p -> {
            p.getFiles().size();
            StringBuilder sb = new StringBuilder();
            sb.append("=== CONTEXTO DO PROJETO: ").append(p.getName()).append(" ===\n");
            if (p.getDescription() != null && !p.getDescription().isBlank())
                sb.append(p.getDescription()).append("\n");
            sb.append("\n");
            for (ProjectFile f : p.getFiles())
                sb.append("[Arquivo: ").append(f.getFilename()).append("]\n")
                  .append(f.getContent()).append("\n\n");
            return sb.toString();
        }).orElse(null);
    }
}