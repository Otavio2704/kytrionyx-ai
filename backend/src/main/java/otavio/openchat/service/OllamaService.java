package otavio.openchat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import otavio.openchat.model.Message;
import otavio.openchat.model.ModelCapabilities;
import otavio.openchat.service.MemoryService;
import otavio.openchat.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {

    private final WebClient ollamaWebClient;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final MemoryService memoryService;
    private final ProjectService projectService;

    // Modelos conhecidos com thinking mode
    private static final Set<String> THINKING_FAMILIES = Set.of(
            "qwen3", "qwen3moe", "deepseek-r1", "qwq", "marco-o1"
    );

    // Modelos conhecidos com visão
    private static final Set<String> VISION_FAMILIES = Set.of(
            "clip", "llava", "moondream", "bakllava", "qwen2-vl",
            "llama3.2-vision", "minicpm-v", "internvl", "phi3-vision"
    );

    private final ExecutorService streamingExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "ollama-stream");
                t.setDaemon(true);
                return t;
            });

    // -------------------------------------------------------------------------
    // Chat com Streaming (SSE)
    // -------------------------------------------------------------------------

    public void streamChat(String model,
                           List<Message> history,
                           Map<String, Object> options,
                           String systemPrompt,
                           List<String> images,
                           UUID projectId,
                           SseEmitter emitter,
                           UUID conversationId) {

        Map<String, Object> payload = buildChatPayload(model, history, options, systemPrompt, images, projectId);

        streamingExecutor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
                StringBuilder fullThinking  = new StringBuilder();
            try {
                Flux<String> tokenFlux = ollamaWebClient.post()
                        .uri("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToFlux(String.class);

                tokenFlux.doOnNext(chunk -> {
                    try {
                        JsonNode node = objectMapper.readTree(chunk);
                        JsonNode messageNode = node.path("message");

                        if (!messageNode.isMissingNode()) {
                            // Envia token de conteúdo normal
                            String token = messageNode.path("content").asText("");
                            if (!token.isEmpty()) {
                                fullResponse.append(token);
                                emitter.send(SseEmitter.event().name("token").data(token));
                            }

                            // Envia token de thinking separado (campo "thinking" do Ollama)
                            // Modelos como Kimi, Qwen3, DeepSeek-R1 retornam raciocínio aqui
                            String thinkingToken = messageNode.path("thinking").asText("");
                            if (!thinkingToken.isEmpty()) {
                                fullThinking.append(thinkingToken);
                                emitter.send(SseEmitter.event().name("thinking").data(thinkingToken));
                            }
                        }

                        if (node.path("done").asBoolean(false)) {
                            String finalContent  = fullResponse.toString();
                            String thinkingContent = fullThinking.toString();

                            // Persiste thinking junto ao content usando separador especial
                            // Formato: <thinking>...</thinking>\n\n<content>
                            String finalResponse = thinkingContent.isBlank()
                                    ? finalContent
                                    : "<thinking>" + thinkingContent + "</thinking>\n\n" + finalContent;

                            conversationService.addMessage(conversationId, "assistant", finalResponse);
                            log.info("Streaming concluído: conversa={}, chars={}, thinking={}",
                                    conversationId, finalContent.length(), thinkingContent.length());
                            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                            emitter.complete();
                        }
                    } catch (IOException e) {
                        log.error("Erro ao processar chunk do Ollama", e);
                    }
                })
                .doOnError(e -> {
                    log.error("Erro no stream do Ollama para conversa {}", conversationId, e);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("Erro ao comunicar com o Ollama: " + e.getMessage()));
                    } catch (IOException ioException) {
                        log.error("Erro ao enviar evento de erro via SSE", ioException);
                    }
                    emitter.completeWithError(e);
                })
                .blockLast();

            } catch (Exception e) {
                log.error("Falha fatal no streaming para conversa {}", conversationId, e);
                emitter.completeWithError(e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Modelos
    // -------------------------------------------------------------------------

    public Mono<Map<String, Object>> listModels() {
        return ollamaWebClient.get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    try {
                        //noinspection unchecked
                        return (Map<String, Object>) objectMapper.readValue(json, Map.class);
                    } catch (Exception e) {
                        log.error("Erro ao parsear lista de modelos", e);
                        return Map.of("models", Collections.emptyList());
                    }
                });
    }

    public Mono<Map<String, Object>> getModelInfo(String modelName) {
        return ollamaWebClient.post()
                .uri("/api/show")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", modelName))
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    try {
                        //noinspection unchecked
                        return (Map<String, Object>) objectMapper.readValue(json, Map.class);
                    } catch (Exception e) {
                        log.error("Erro ao parsear info do modelo '{}'", modelName, e);
                        return Map.of("error", "Erro ao parsear resposta do Ollama");
                    }
                });
    }

    /**
     * Detecta as capabilities de um modelo a partir do /api/show.
     * Analisa families, capabilities e nome do modelo para inferir suporte
     * a thinking mode, visão e contexto máximo.
     */
    public Mono<ModelCapabilities> getModelCapabilities(String modelName) {
        return ollamaWebClient.post()
                .uri("/api/show")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", modelName))
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> parseCapabilities(modelName, json))
                .onErrorReturn(buildFallbackCapabilities(modelName));
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    private ModelCapabilities parseCapabilities(String modelName, String json) {
        boolean supportsThinking = false;
        boolean supportsVision   = false;
        int     contextLength    = 0;
        String  family           = "";
        String  parameterSize    = "";

        try {
            JsonNode root = objectMapper.readTree(json);

            // 1 — Lê capabilities explícitas do Ollama (versões mais novas)
            JsonNode capabilities = root.path("capabilities");
            if (capabilities.isArray()) {
                for (JsonNode cap : capabilities) {
                    String c = cap.asText().toLowerCase();
                    if (c.contains("think"))  supportsThinking = true;
                    if (c.contains("vision") || c.contains("image")) supportsVision = true;
                }
            }

            // 2 — Lê families dos details
            JsonNode details = root.path("details");
            if (!details.isMissingNode()) {
                family        = details.path("family").asText("");
                parameterSize = details.path("parameter_size").asText("");

                JsonNode families = details.path("families");
                if (families.isArray()) {
                    for (JsonNode f : families) {
                        String fam = f.asText().toLowerCase();
                        if (THINKING_FAMILIES.stream().anyMatch(fam::contains)) supportsThinking = true;
                        if (VISION_FAMILIES.stream().anyMatch(fam::contains))   supportsVision   = true;
                    }
                }

                // Lê context length dos details ou model_info
                JsonNode ctxNode = details.path("context_length");
                if (!ctxNode.isMissingNode()) contextLength = ctxNode.asInt(0);
            }

            // 3 — Lê context_length do model_info se disponível
            JsonNode modelInfo = root.path("model_info");
            if (!modelInfo.isMissingNode() && contextLength == 0) {
                // Tenta campos comuns onde o context length aparece
                for (String field : List.of("context_length", "num_ctx", "max_position_embeddings")) {
                    JsonNode ctxNode = modelInfo.path(field);
                    if (!ctxNode.isMissingNode() && ctxNode.isInt()) {
                        contextLength = ctxNode.asInt(0);
                        break;
                    }
                }
                // Fallback: procura em qualquer campo que contenha "context" no nome
                if (contextLength == 0) {
                    Iterator<Map.Entry<String, JsonNode>> fields = modelInfo.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        if (entry.getKey().toLowerCase().contains("context") && entry.getValue().isInt()) {
                            contextLength = entry.getValue().asInt(0);
                            break;
                        }
                    }
                }
            }

            // 4 — Fallback: infere pelo nome do modelo
            String nameLower = modelName.toLowerCase();
            if (!supportsThinking) {
                supportsThinking = THINKING_FAMILIES.stream().anyMatch(nameLower::contains)
                        || nameLower.contains("qwen3")
                        || nameLower.contains("r1")
                        || nameLower.contains("qwq");
            }
            if (!supportsVision) {
                supportsVision = VISION_FAMILIES.stream().anyMatch(nameLower::contains);
            }

            // 5 — Context length padrão se não encontrado
            if (contextLength == 0) {
                contextLength = inferContextLength(nameLower);
            }

        } catch (Exception e) {
            log.warn("Erro ao parsear capabilities do modelo '{}': {}", modelName, e.getMessage());
        }

        return ModelCapabilities.builder()
                .modelName(modelName)
                .supportsThinking(supportsThinking)
                .supportsVision(supportsVision)
                .contextLength(contextLength)
                .family(family)
                .parameterSize(parameterSize)
                .build();
    }

    /**
     * Infere o context length pelo nome do modelo quando o /api/show não retorna.
     */
    private int inferContextLength(String nameLower) {
        if (nameLower.contains("minimax"))  return 1000000;
        if (nameLower.contains("kimi"))     return 131072;
        if (nameLower.contains("qwen3"))    return 32768;
        if (nameLower.contains("nemotron")) return 32768;
        return 4096; // padrão conservador
    }

    /**
     * Capabilities mínimas usadas quando o /api/show falha (modelo cloud offline etc).
     */
    /**
     * Mescla o system prompt de memória com o system prompt manual do usuário.
     * A memória vem primeiro para ter mais peso no contexto do modelo.
     */
    private String mergeSystemPrompts(String memoryPrompt, String userSystemPrompt) {
        boolean hasMemory = memoryPrompt != null && !memoryPrompt.isBlank();
        boolean hasUser   = userSystemPrompt != null && !userSystemPrompt.isBlank();
        if (hasMemory && hasUser)  return memoryPrompt + "\n\n" + userSystemPrompt;
        if (hasMemory)             return memoryPrompt;
        if (hasUser)               return userSystemPrompt;
        return null;
    }

    private ModelCapabilities buildFallbackCapabilities(String modelName) {
        String nameLower = modelName.toLowerCase();
        return ModelCapabilities.builder()
                .modelName(modelName)
                .supportsThinking(nameLower.contains("qwen3") || nameLower.contains("r1") || nameLower.contains("qwq"))
                .supportsVision(VISION_FAMILIES.stream().anyMatch(nameLower::contains))
                .contextLength(inferContextLength(nameLower))
                .family("")
                .parameterSize("")
                .build();
    }

    private Map<String, Object> buildChatPayload(String model,
                                                  List<Message> history,
                                                  Map<String, Object> options,
                                                  String systemPrompt,
                                                  List<String> images,
                                                  UUID projectId) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // Monta system prompt final mesclando memória + contexto de projeto + system prompt
        String memoryPrompt  = memoryService.buildMemorySystemPrompt();
        String projectContext = projectId != null ? projectService.buildProjectContext(projectId) : null;
        String combined = mergeSystemPrompts(memoryPrompt, projectContext);
        String finalSystemPrompt = mergeSystemPrompts(combined, systemPrompt);
        if (finalSystemPrompt != null && !finalSystemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", finalSystemPrompt));
        }

        // Adiciona mensagens do histórico
        for (int i = 0; i < history.size(); i++) {
            Message msg = history.get(i);
            // Imagens só são anexadas à última mensagem do usuário
            boolean isLastUserMsg = msg.getRole().equals("user") && i == history.size() - 1
                    && images != null && !images.isEmpty();

            if (isLastUserMsg) {
                Map<String, Object> msgMap = new HashMap<>();
                msgMap.put("role", msg.getRole());
                msgMap.put("content", msg.getContent());
                msgMap.put("images", images); // base64 strings para modelos com visão
                messages.add(msgMap);
            } else {
                messages.add(new HashMap<>(msg.toOllamaMap()));
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("stream", true);

        if (options != null && !options.isEmpty()) {
            payload.put("options", options);
        }

        return payload;
    }
}