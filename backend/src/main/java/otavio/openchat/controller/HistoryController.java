package otavio.openchat.controller;

import otavio.openchat.model.ConversationDTO;
import otavio.openchat.service.ConversationService;
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
    public ResponseEntity<List<ConversationDTO>> listConversations() {
        List<ConversationDTO> list = conversationService.getAllConversations()
                .stream()
                .map(ConversationDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDTO> getConversation(@PathVariable UUID id) {
        return conversationService.getConversationById(id)
                .map(ConversationDTO::fromEntityWithMessages)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("Conversa não encontrada: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * GET /api/history/project/{projectId}
     * Lista todos os chats vinculados a um projeto específico.
     * Usado para exibir os chats dentro do modal de detalhes do projeto.
     */
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<ConversationDTO>> getConversationsByProject(@PathVariable UUID projectId) {
        List<ConversationDTO> list = conversationService.getConversationsByProject(projectId)
                .stream()
                .map(ConversationDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/{id}/pin")
    public ResponseEntity<Map<String, Object>> togglePin(@PathVariable UUID id) {
        boolean success = conversationService.togglePin(id);
        if (!success) {
            return ResponseEntity.ok(Map.of("success", false, "reason", "limit_reached"));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/{id}/rename")
    public ResponseEntity<Void> renameConversation(@PathVariable UUID id,
                                                    @RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isBlank()) return ResponseEntity.badRequest().build();
        return conversationService.renameConversation(id, title.trim())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        boolean deleted = conversationService.deleteConversation(id);
        if (deleted) {
            log.info("Conversa {} deletada", id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}