package otavio.openchat.repository;

import otavio.openchat.model.ProjectFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectFileRepository extends JpaRepository<ProjectFile, UUID> {
    List<ProjectFile> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
    void deleteByProjectId(UUID projectId);
}