package otavio.kyronai.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping("/actions/{conversationId}")
    public ResponseEntity<List<AgentActionDTO>> list(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(agentService.getActionsByConversation(conversationId));
    }

    @GetMapping("/actions/{conversationId}/pending-count")
    public ResponseEntity<Map<String, Long>> pendingCount(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(
                Map.of("pendingCount", agentService.countPendingActions(conversationId)));
    }

    @PostMapping("/actions/{actionId}/approve")
    public ResponseEntity<AgentActionDTO> approve(@PathVariable UUID actionId) {
        try {
            return ResponseEntity.ok(agentService.approveAction(actionId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/actions/{actionId}/reject")
    public ResponseEntity<AgentActionDTO> reject(@PathVariable UUID actionId) {
        try {
            return ResponseEntity.ok(agentService.rejectAction(actionId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}