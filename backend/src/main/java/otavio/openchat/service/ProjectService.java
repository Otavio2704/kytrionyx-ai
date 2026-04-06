package otavio.openchat.service;

import otavio.openchat.model.Project;
import otavio.openchat.model.ProjectFile;
import otavio.openchat.repository.ProjectFileRepository;
import otavio.openchat.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;

    @Transactional(readOnly = true)
    public List<Project> getAllProjects() {
        return projectRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Project> getProjectById(UUID id) {
        return projectRepository.findById(id).map(p -> {
            p.getFiles().size(); // força carregamento LAZY
            return p;
        });
    }

    @Transactional
    public Project createProject(String name, String description) {
        Project project = Project.builder()
                .name(name)
                .description(description)
                .build();
        return projectRepository.save(project);
    }

    @Transactional
    public Optional<Project> updateProject(UUID id, String name, String description) {
        return projectRepository.findById(id).map(p -> {
            if (name != null && !name.isBlank()) p.setName(name);
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
    public ProjectFile addFile(UUID projectId, String filename, String fileType, String content) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Projeto não encontrado: " + projectId));

        // Limita o conteúdo a 50k chars por arquivo para não explodir o contexto
        String truncated = content.length() > 50_000
                ? content.substring(0, 50_000) + "\n[... truncado ...]"
                : content;

        ProjectFile file = ProjectFile.builder()
                .project(project)
                .filename(filename)
                .fileType(fileType)
                .content(truncated)
                .build();

        ProjectFile saved = projectFileRepository.save(file);
        log.info("Arquivo '{}' adicionado ao projeto '{}'", filename, project.getName());
        return saved;
    }

    @Transactional
    public boolean deleteFile(UUID fileId) {
        if (!projectFileRepository.existsById(fileId)) return false;
        projectFileRepository.deleteById(fileId);
        return true;
    }

    /**
     * Monta o bloco de contexto do projeto para injetar no system prompt.
     * Formato:
     * === CONTEXTO DO PROJETO: Nome ===
     * Descrição...
     *
     * [Arquivo: nome.pdf]
     * conteúdo extraído...
     */
    @Transactional(readOnly = true)
    public String buildProjectContext(UUID projectId) {
        return projectRepository.findById(projectId).map(project -> {
            project.getFiles().size();
            StringBuilder sb = new StringBuilder();
            sb.append("=== CONTEXTO DO PROJETO: ").append(project.getName()).append(" ===\n");
            if (project.getDescription() != null && !project.getDescription().isBlank()) {
                sb.append(project.getDescription()).append("\n");
            }
            sb.append("\n");
            for (ProjectFile file : project.getFiles()) {
                sb.append("[Arquivo: ").append(file.getFilename()).append("]\n");
                sb.append(file.getContent()).append("\n\n");
            }
            return sb.toString();
        }).orElse(null);
    }
}