package otavio.openchat.repository;

import otavio.openchat.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Lista todas as conversas ordenadas pela mais recente (updated_at DESC).
     * Não carrega as mensagens (LAZY) — ideal para popular a sidebar.
     */
    List<Conversation> findAllByOrderByUpdatedAtDesc();

    /**
     * Busca conversas pelo nome do modelo utilizado.
     * Útil para filtros futuros na interface.
     */
    List<Conversation> findByModelNameOrderByUpdatedAtDesc(String modelName);

    /**
     * Verifica se uma conversa existe antes de tentar deletá-la,
     * evitando exceções desnecessárias no service.
     */
    boolean existsById(UUID id);

    /**
     * Busca conversas cujo título contém o termo pesquisado (case-insensitive).
     * Base para futura funcionalidade de busca no histórico.
     */
    @Query("SELECT c FROM Conversation c WHERE LOWER(c.title) LIKE LOWER(CONCAT('%', :term, '%')) ORDER BY c.updatedAt DESC")
    List<Conversation> searchByTitle(String term);
}
