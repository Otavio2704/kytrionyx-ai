package otavio.kyronai.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final ConversationService conversationService;

    @GetMapping
    public ResponseEntity<List<ConversationDTO>> list() {
        return ResponseEntity.ok(
                conversationService.getAllConversations().stream()
                        .map(ConversationDTO::fromEntity).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDTO> get(@PathVariable UUID id) {
        return conversationService.getConversationById(id)
                .map(ConversationDTO::fromEntityWithMessages)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<ConversationDTO>> byProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(
                conversationService.getConversationsByProject(projectId).stream()
                        .map(ConversationDTO::fromEntity).toList());
    }

    @PatchMapping("/{id}/pin")
    public ResponseEntity<Map<String, Object>> pin(@PathVariable UUID id) {
        boolean ok = conversationService.togglePin(id);
        return ok
                ? ResponseEntity.ok(Map.of("success", true))
                : ResponseEntity.ok(Map.of("success", false, "reason", "limit_reached"));
    }

    @PatchMapping("/{id}/rename")
    public ResponseEntity<Void> rename(@PathVariable UUID id,
                                        @RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isBlank()) return ResponseEntity.badRequest().build();
        return conversationService.renameConversation(id, title.trim())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return conversationService.deleteConversation(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}