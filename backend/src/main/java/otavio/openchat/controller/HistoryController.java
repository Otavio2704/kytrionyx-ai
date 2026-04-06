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

    /**
     * GET /api/history
     * Lista todas as conversas sem mensagens (para a sidebar).
     */
    @GetMapping
    public ResponseEntity<List<ConversationDTO>> listConversations() {
        List<ConversationDTO> list = conversationService.getAllConversations()
                .stream()
                .map(ConversationDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    /**
     * GET /api/history/{id}
     * Retorna uma conversa com todas as mensagens.
     */
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
     * PATCH /api/history/{id}/pin
     * Alterna o estado de pin. Máximo 3 fixados.
     */
    @PatchMapping("/{id}/pin")
    public ResponseEntity<Map<String, Object>> togglePin(@PathVariable UUID id) {
        boolean success = conversationService.togglePin(id);
        if (!success) {
            return ResponseEntity.ok(Map.of("success", false, "reason", "limit_reached"));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * PATCH /api/history/{id}/rename
     * Renomeia o título de uma conversa sem alterar as mensagens.
     */
    @PatchMapping("/{id}/rename")
    public ResponseEntity<Void> renameConversation(@PathVariable UUID id,
                                                    @RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isBlank()) return ResponseEntity.badRequest().build();
        return conversationService.renameConversation(id, title.trim())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * DELETE /api/history/{id}
     * Deleta uma conversa e todas as suas mensagens.
     */
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