package otavio.openchat.controller;

import otavio.openchat.model.Message;
import otavio.openchat.service.ConversationService;
import otavio.openchat.service.OllamaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final OllamaService ollamaService;
    private final ConversationService conversationService;

    /**
     * POST /api/chat
     * Envia mensagem para conversa existente com resposta em streaming (SSE).
     *
     * Payload:
     * {
     *   "conversationId": "uuid",
     *   "message": "texto",
     *   "model": "llama3.2",
     *   "options": { "temperature": 0.7 },
     *   "systemPrompt": "...",
     *   "images": ["base64..."]   <- opcional, apenas para modelos com visão
     * }
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, Object> payload) {
        UUID   conversationId = UUID.fromString((String) payload.get("conversationId"));
        String userMessage    = (String) payload.get("message");
        String model          = (String) payload.getOrDefault("model", "llama3.2");

        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) payload.getOrDefault("options", Map.of());
        String systemPrompt = (String) payload.getOrDefault("systemPrompt", null);

        @SuppressWarnings("unchecked")
        List<String> images = (List<String>) payload.getOrDefault("images", List.of());
        String projectIdStr = (String) payload.getOrDefault("projectId", null);
        UUID projectId = projectIdStr != null ? UUID.fromString(projectIdStr) : null;

        SseEmitter emitter = new SseEmitter(180_000L);
        conversationService.addMessage(conversationId, "user", userMessage);
        List<Message> history = conversationService.getMessages(conversationId);
        ollamaService.streamChat(model, history, options, systemPrompt, images, projectId, emitter, conversationId);
        return emitter;
    }

    /**
     * POST /api/chat/new
     * Cria nova conversa e envia primeira mensagem via SSE.
     * Retorna o UUID no header X-Conversation-Id e no evento SSE "conversation-id".
     */
    @PostMapping(value = "/new", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter newChat(@RequestBody Map<String, Object> payload) {
        String userMessage  = (String) payload.get("message");
        String model        = (String) payload.getOrDefault("model", "llama3.2");

        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) payload.getOrDefault("options", Map.of());
        String systemPrompt = (String) payload.getOrDefault("systemPrompt", null);

        @SuppressWarnings("unchecked")
        List<String> images = (List<String>) payload.getOrDefault("images", List.of());
        String projectIdStr = (String) payload.getOrDefault("projectId", null);
        UUID projectId = projectIdStr != null ? UUID.fromString(projectIdStr) : null;

        String title          = userMessage.length() > 50 ? userMessage.substring(0, 50) : userMessage;
        UUID   conversationId = conversationService.createConversation(title, model);

        conversationService.addMessage(conversationId, "user", userMessage);
        List<Message> history = conversationService.getMessages(conversationId);

        SseEmitter emitter = new SseEmitter(180_000L);

        try {
            emitter.send(SseEmitter.event()
                    .name("conversation-id")
                    .data(conversationId.toString()));
        } catch (IOException e) {
            log.error("Erro ao enviar conversation-id via SSE", e);
        }

        ollamaService.streamChat(model, history, options, systemPrompt, images, projectId, emitter, conversationId);

        return emitter;
    }
}