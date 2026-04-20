package otavio.kyronai.code;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CodeSessionRepository extends JpaRepository<CodeSession, UUID> {
    Optional<CodeSession> findByConversationId(UUID conversationId);
}