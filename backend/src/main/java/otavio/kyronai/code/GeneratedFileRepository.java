package otavio.kyronai.code;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GeneratedFileRepository extends JpaRepository<GeneratedFile, UUID> {
    Optional<GeneratedFile> findByCodeSessionIdAndFilePath(UUID codeSessionId, String filePath);
    void deleteByCodeSessionId(UUID codeSessionId);
}