package otavio.openchat.repository;

import otavio.openchat.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findAllByOrderByUpdatedAtDesc();

    List<Conversation> findByModelNameOrderByUpdatedAtDesc(String modelName);

    boolean existsById(UUID id);

    @Query("SELECT c FROM Conversation c WHERE LOWER(c.title) LIKE LOWER(CONCAT('%', :term, '%')) ORDER BY c.updatedAt DESC")
    List<Conversation> searchByTitle(String term);

    /**
     * Retorna todos os chats vinculados a um projeto, do mais recente ao mais antigo.
     * Usado para listar conversas dentro do modal de detalhes do projeto.
     */
    List<Conversation> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);
}