package otavio.openchat.repository;

import otavio.openchat.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Retorna todas as mensagens de uma conversa ordenadas por data de criação.
     * Usado para montar o histórico de contexto enviado ao Ollama.
     */
    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /**
     * Conta quantas mensagens uma conversa possui.
     * Útil para paginação futura ou exibição de contadores na sidebar.
     */
    long countByConversationId(UUID conversationId);

    /**
     * Deleta todas as mensagens de uma conversa.
     * Normalmente gerenciado em cascata pelo CascadeType.ALL na entidade Conversation,
     * mas disponível aqui para operações manuais se necessário.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Message m WHERE m.conversation.id = :conversationId")
    void deleteAllByConversationId(UUID conversationId);

    /**
     * Retorna apenas as N mensagens mais recentes de uma conversa.
     * Útil para limitar o contexto enviado ao Ollama em conversas longas.
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId ORDER BY m.createdAt DESC LIMIT :limit")
    List<Message> findLastNByConversationId(UUID conversationId, int limit);
}
