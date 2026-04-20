package otavio.kyronai.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentActionRepository extends JpaRepository<AgentAction, UUID> {

    List<AgentAction> findByConversationIdOrderByExecutionOrderAsc(UUID conversationId);

    long countByConversationIdAndStatus(UUID conversationId, AgentAction.ActionStatus status);
}