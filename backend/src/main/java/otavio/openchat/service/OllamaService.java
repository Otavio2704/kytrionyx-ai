package otavio.openchat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import otavio.openchat.model.Message;
import otavio.openchat.model.ModelCapabilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
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

    private final WebClient httpClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();

    private static final String IDENTITY_PROMPT =
            "Você é o OpenChat, um assistente de IA local criado para rodar inteiramente " +
            "no hardware do usuário via Ollama. Você é direto, técnico quando necessário, " +
            "e sempre respeita a privacidade do usuário — nenhum dado é enviado para " +
            "servidores externos. Responda sempre no idioma em que o usuário escrever.";

    private static final Set<String> THINKING_FAMILIES = Set.of(
            "qwen3", "qwen3moe", "deepseek-r1", "qwq", "marco-o1");

    private static final Set<String> VISION_FAMILIES = Set.of(
            "clip", "llava", "moondream", "bakllava", "qwen2-vl",
            "llama3.2-vision", "minicpm-v", "internvl", "phi3-vision");

    private final ExecutorService streamingExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "ollama-stream");
                t.setDaemon(true);
                return t;
            });

    // -------------------------------------------------------------------------
    // Chat com Streaming
    // -------------------------------------------------------------------------

    public void streamChat(String model,
                           List<Message> history,
                           Map<String, Object> options,
                           String systemPrompt,
                           List<String> images,
                           UUID projectId,
                           boolean webSearchEnabled,
                           SseEmitter emitter,
                           UUID conversationId) {

        streamingExecutor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            StringBuilder fullThinking = new StringBuilder();
            try {
                String webSearchContext = null;
                if (webSearchEnabled && !history.isEmpty()) {
                    String query = history.get(history.size() - 1).getContent();
                    try {
                        emitter.send(SseEmitter.event().name("search-start").data("Buscando na web..."));
                        webSearchContext = fetchWebSearchContext(query);
                        emitter.send(SseEmitter.event().name("search-done").data("ok"));
                        log.info("Web search RAG: {} chars de contexto", webSearchContext != null ? webSearchContext.length() : 0);
                    } catch (Exception e) {
                        log.warn("Web search falhou: {}", e.getMessage());
                        try { emitter.send(SseEmitter.event().name("search-done").data("failed")); } catch (IOException ignored) {}
                    }
                }

                Map<String, Object> payload = buildChatPayload(
                        model, history, options, systemPrompt, images, projectId, webSearchContext);

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
                            String token = messageNode.path("content").asText("");
                            if (!token.isEmpty()) {
                                fullResponse.append(token);
                                emitter.send(SseEmitter.event().name("token").data(token));
                            }
                            String thinkingToken = messageNode.path("thinking").asText("");
                            if (!thinkingToken.isEmpty()) {
                                fullThinking.append(thinkingToken);
                                emitter.send(SseEmitter.event().name("thinking").data(thinkingToken));
                            }
                        }
                        if (node.path("done").asBoolean(false)) {
                            String fc = fullResponse.toString();
                            String tc = fullThinking.toString();
                            String finalResponse = tc.isBlank() ? fc
                                    : "<thinking>" + tc + "</thinking>\n\n" + fc;
                            conversationService.addMessage(conversationId, "assistant", finalResponse);
                            log.info("Streaming concluído: conversa={}, chars={}", conversationId, fc.length());
                            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                            emitter.complete();
                        }
                    } catch (IOException e) {
                        log.error("Erro ao processar chunk", e);
                    }
                })
                .doOnError(e -> {
                    log.error("Erro no stream: conversa={}", conversationId, e);
                    try {
                        emitter.send(SseEmitter.event().name("error")
                                .data("Erro ao comunicar com o Ollama: " + e.getMessage()));
                    } catch (IOException ignored) {}
                    emitter.completeWithError(e);
                })
                .blockLast();

            } catch (Exception e) {
                log.error("Falha fatal no streaming: conversa={}", conversationId, e);
                emitter.completeWithError(e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Web Search RAG — DuckDuckGo Instant Answer API
    // Gratuita, sem chave, sem limite de uso para uso pessoal.
    // Retorna: abstracts (Wikipedia), respostas diretas, tópicos relacionados.
    // -------------------------------------------------------------------------

    private String fetchWebSearchContext(String query) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://api.duckduckgo.com/")
                    .queryParam("q", query)
                    .queryParam("format", "json")
                    .queryParam("no_html", "1")
                    .queryParam("skip_disambig", "1")
                    .build().toUriString();

            String body = httpClient.get()
                    .uri(url)
                    .header("User-Agent", "OpenChat/1.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(8));

            if (body == null || body.isBlank()) return null;

            JsonNode root = objectMapper.readTree(body);
            StringBuilder ctx = new StringBuilder();

            String abstractText   = root.path("Abstract").asText("");
            String abstractSource = root.path("AbstractSource").asText("");
            if (!abstractText.isBlank()) {
                ctx.append("**").append(abstractSource.isEmpty() ? "Resumo" : abstractSource)
                   .append(":** ").append(abstractText).append("\n\n");
            }

            String answer = root.path("Answer").asText("");
            if (!answer.isBlank()) {
                ctx.append("**Resposta direta:** ").append(answer).append("\n\n");
            }

            JsonNode relatedTopics = root.path("RelatedTopics");
            int count = 0;
            if (relatedTopics.isArray()) {
                for (JsonNode topic : relatedTopics) {
                    if (count >= 5) break;
                    String text = topic.path("Text").asText("");
                    if (!text.isBlank()) { ctx.append("- ").append(text).append("\n"); count++; }
                }
            }

            JsonNode results = root.path("Results");
            if (results.isArray()) {
                for (JsonNode result : results) {
                    String text = result.path("Text").asText("");
                    String url2 = result.path("FirstURL").asText("");
                    if (!text.isBlank()) {
                        ctx.append("\n**Fonte:** ").append(url2).append("\n").append(text).append("\n");
                    }
                }
            }

            String out = ctx.toString().trim();
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            log.warn("Erro web search '{}': {}", query, e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Modelos
    // -------------------------------------------------------------------------

    public Mono<Map<String, Object>> listModels() {
        return ollamaWebClient.get().uri("/api/tags").retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    try { //noinspection unchecked
                        return (Map<String, Object>) objectMapper.readValue(json, Map.class);
                    } catch (Exception e) {
                        log.error("Erro ao parsear modelos", e);
                        return Map.of("models", Collections.emptyList());
                    }
                });
    }

    public Mono<Map<String, Object>> getModelInfo(String modelName) {
        return ollamaWebClient.post().uri("/api/show")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", modelName))
                .retrieve().bodyToMono(String.class)
                .map(json -> {
                    try { //noinspection unchecked
                        return (Map<String, Object>) objectMapper.readValue(json, Map.class);
                    } catch (Exception e) {
                        log.error("Erro ao parsear info do modelo '{}'", modelName, e);
                        return Map.of("error", "Erro ao parsear resposta do Ollama");
                    }
                });
    }

    public Mono<ModelCapabilities> getModelCapabilities(String modelName) {
        return ollamaWebClient.post().uri("/api/show")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", modelName))
                .retrieve().bodyToMono(String.class)
                .map(json -> parseCapabilities(modelName, json))
                .onErrorReturn(buildFallbackCapabilities(modelName));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ModelCapabilities parseCapabilities(String modelName, String json) {
        boolean supportsThinking = false, supportsVision = false;
        int contextLength = 0;
        String family = "", parameterSize = "";
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode capabilities = root.path("capabilities");
            if (capabilities.isArray()) {
                for (JsonNode cap : capabilities) {
                    String c = cap.asText().toLowerCase();
                    if (c.contains("think")) supportsThinking = true;
                    if (c.contains("vision") || c.contains("image")) supportsVision = true;
                }
            }
            JsonNode details = root.path("details");
            if (!details.isMissingNode()) {
                family = details.path("family").asText("");
                parameterSize = details.path("parameter_size").asText("");
                JsonNode families = details.path("families");
                if (families.isArray()) {
                    for (JsonNode f : families) {
                        String fam = f.asText().toLowerCase();
                        if (THINKING_FAMILIES.stream().anyMatch(fam::contains)) supportsThinking = true;
                        if (VISION_FAMILIES.stream().anyMatch(fam::contains)) supportsVision = true;
                    }
                }
                JsonNode ctxNode = details.path("context_length");
                if (!ctxNode.isMissingNode()) contextLength = ctxNode.asInt(0);
            }
            JsonNode modelInfo = root.path("model_info");
            if (!modelInfo.isMissingNode() && contextLength == 0) {
                for (String field : List.of("context_length", "num_ctx", "max_position_embeddings")) {
                    JsonNode n = modelInfo.path(field);
                    if (!n.isMissingNode() && n.isInt()) { contextLength = n.asInt(0); break; }
                }
                if (contextLength == 0) {
                    var it = modelInfo.fields();
                    while (it.hasNext()) {
                        var e = it.next();
                        if (e.getKey().toLowerCase().contains("context") && e.getValue().isInt()) {
                            contextLength = e.getValue().asInt(0); break;
                        }
                    }
                }
            }
            String nl = modelName.toLowerCase();
            if (!supportsThinking) supportsThinking = THINKING_FAMILIES.stream().anyMatch(nl::contains)
                    || nl.contains("qwen3") || nl.contains("r1") || nl.contains("qwq");
            if (!supportsVision) supportsVision = VISION_FAMILIES.stream().anyMatch(nl::contains);
            if (contextLength == 0) contextLength = inferContextLength(nl);
        } catch (Exception e) {
            log.warn("Erro ao parsear capabilities '{}': {}", modelName, e.getMessage());
        }
        return ModelCapabilities.builder().modelName(modelName)
                .supportsThinking(supportsThinking).supportsVision(supportsVision)
                .contextLength(contextLength).family(family).parameterSize(parameterSize).build();
    }

    private int inferContextLength(String n) {
        if (n.contains("minimax")) return 1000000;
        if (n.contains("kimi")) return 131072;
        if (n.contains("qwen3")) return 32768;
        if (n.contains("nemotron")) return 32768;
        return 4096;
    }

    private ModelCapabilities buildFallbackCapabilities(String modelName) {
        String nl = modelName.toLowerCase();
        return ModelCapabilities.builder().modelName(modelName)
                .supportsThinking(nl.contains("qwen3") || nl.contains("r1") || nl.contains("qwq"))
                .supportsVision(VISION_FAMILIES.stream().anyMatch(nl::contains))
                .contextLength(inferContextLength(nl)).family("").parameterSize("").build();
    }

    private String mergeSystemPrompts(String a, String b) {
        boolean ha = a != null && !a.isBlank(), hb = b != null && !b.isBlank();
        if (ha && hb) return a + "\n\n" + b;
        if (ha) return a;
        if (hb) return b;
        return null;
    }

    private Map<String, Object> buildChatPayload(String model, List<Message> history,
                                                  Map<String, Object> options, String systemPrompt,
                                                  List<String> images, UUID projectId,
                                                  String webSearchContext) {
        List<Map<String, Object>> messages = new ArrayList<>();

        String memoryPrompt   = memoryService.buildMemorySystemPrompt();
        String projectContext = projectId != null ? projectService.buildProjectContext(projectId) : null;
        String webRagBlock    = null;
        if (webSearchContext != null && !webSearchContext.isBlank()) {
            webRagBlock = "## Contexto atualizado da web\n\n" + webSearchContext
                    + "\n\nUse estas informações como contexto adicional. Se não forem relevantes, ignore-as.";
        }

        String sp = mergeSystemPrompts(IDENTITY_PROMPT, memoryPrompt);
        sp = mergeSystemPrompts(sp, projectContext);
        sp = mergeSystemPrompts(sp, webRagBlock);
        sp = mergeSystemPrompts(sp, systemPrompt);
        messages.add(Map.of("role", "system", "content", sp));

        for (int i = 0; i < history.size(); i++) {
            Message msg = history.get(i);
            boolean isLastUser = msg.getRole().equals("user") && i == history.size() - 1
                    && images != null && !images.isEmpty();
            if (isLastUser) {
                Map<String, Object> m = new HashMap<>();
                m.put("role", msg.getRole()); m.put("content", msg.getContent()); m.put("images", images);
                messages.add(m);
            } else {
                messages.add(new HashMap<>(msg.toOllamaMap()));
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("stream", true);
        if (options != null && !options.isEmpty()) payload.put("options", options);
        return payload;
    }
}