package otavio.kyronai.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findAllByOrderByUpdatedAtDesc();

    List<Conversation> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    @Query("SELECT c FROM Conversation c WHERE LOWER(c.title) LIKE LOWER(CONCAT('%', :term, '%')) ORDER BY c.updatedAt DESC")
    List<Conversation> searchByTitle(String term);
}