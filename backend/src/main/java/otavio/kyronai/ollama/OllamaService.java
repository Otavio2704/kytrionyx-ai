package otavio.kyronai.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriComponentsBuilder;
import otavio.kyronai.agent.AgentService;
import otavio.kyronai.chat.ConversationService;
import otavio.kyronai.chat.Message;
import otavio.kyronai.code.CodeGenerationService;
import otavio.kyronai.github.GitHubService;
import otavio.kyronai.memory.MemoryService;
import otavio.kyronai.models.ModelCapabilities;
import otavio.kyronai.project.ProjectService;
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

    private final WebClient             ollamaWebClient;
    private final ObjectMapper          objectMapper;
    private final ConversationService   conversationService;
    private final MemoryService         memoryService;
    private final ProjectService        projectService;
    private final CodeGenerationService codeGenerationService;
    private final AgentService          agentService;
    private final GitHubService         gitHubService;

    @Value("${searxng.url:http://localhost:8081}")
    private String searxngUrl;

    private final WebClient httpClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();

    private static final String WEB_CONTEXT_PREFIX = "[WEB_SEARCH_CONTEXT]\n";

    private static final String IDENTITY_PROMPT =
            "Você é o Kyron AI, um assistente de IA local criado para rodar inteiramente " +
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

    // =========================================================================
    // Streaming principal
    // =========================================================================

    public void streamChat(String model,
                           List<Message> history,
                           Map<String, Object> options,
                           String systemPrompt,
                           List<String> images,
                           UUID projectId,
                           boolean webSearchEnabled,
                           boolean codeMode,
                           boolean agentMode,
                           UUID githubRepoId,
                           SseEmitter emitter,
                           UUID conversationId) {

        boolean thinkingEnabled = options != null
                && Boolean.TRUE.equals(options.get("think"));

        streamingExecutor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            StringBuilder fullThinking = new StringBuilder();

            try {
                // 1. Contextos web acumulados de mensagens anteriores
                List<String> webContexts = extractWebContexts(history);

                // 2. Web search na mensagem atual
                String lastUserMsg = lastUserMessage(history);
                boolean shouldSearch = webSearchEnabled
                        && !lastUserMsg.isBlank()
                        && !isFollowUp(lastUserMsg);

                if (shouldSearch) {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("search-start").data("Buscando na web..."));
                        String freshCtx = fetchWebSearchContext(lastUserMsg);
                        if (freshCtx != null && !freshCtx.isBlank()) {
                            conversationService.addMessage(
                                    conversationId, "tool",
                                    WEB_CONTEXT_PREFIX + freshCtx, false);
                            webContexts.add(freshCtx);
                            emitter.send(SseEmitter.event().name("search-done").data("ok"));
                            emitter.send(SseEmitter.event().name("search-sources").data(freshCtx));
                        } else {
                            emitter.send(SseEmitter.event().name("search-done").data("empty"));
                        }
                    } catch (Exception e) {
                        log.error("Web search falhou: conversa={}", conversationId, e);
                        try { emitter.send(SseEmitter.event().name("search-done").data("failed")); }
                        catch (IOException ignored) {}
                    }
                } else if (webSearchEnabled && !webContexts.isEmpty()) {
                    emitter.send(SseEmitter.event().name("search-done").data("cached"));
                }

                // 3. Contexto GitHub
                String githubContext = null;
                if (githubRepoId != null) {
                    githubContext = gitHubService.buildContext(githubRepoId);
                    if (githubContext != null)
                        emitter.send(SseEmitter.event().name("github-context").data("injected"));
                }

                // 4. Monta payload
                Map<String, Object> payload = buildPayload(
                        model, history, options, systemPrompt, images,
                        projectId, webContexts, codeMode, agentMode, githubContext);

                // 5. Stream de tokens
                Flux<String> flux = ollamaWebClient.post()
                        .uri("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToFlux(String.class);

                flux.doOnNext(chunk -> {
                    try {
                        JsonNode node    = objectMapper.readTree(chunk);
                        JsonNode msgNode = node.path("message");

                        if (!msgNode.isMissingNode()) {
                            String token = msgNode.path("content").asText("");
                            if (!token.isEmpty()) {
                                fullResponse.append(token);
                                emitter.send(SseEmitter.event().name("token").data(token));
                            }
                            String think = msgNode.path("thinking").asText("");
                            if (!think.isEmpty()) {
                                fullThinking.append(think);
                                emitter.send(SseEmitter.event().name("thinking").data(think));
                            }
                        }

                        if (node.path("done").asBoolean(false)) {
                            String fc = fullResponse.toString();
                            String tc = fullThinking.toString();
                            String finalResponse = tc.isBlank()
                                    ? fc
                                    : "<thinking>" + tc + "</thinking>\n\n" + fc;

                            conversationService.addMessage(
                                    conversationId, "assistant",
                                    finalResponse, thinkingEnabled);

                            // 6. Pós-processamento
                            if (codeMode  && !fc.isBlank()) processCodeMode(conversationId, fc, emitter);
                            if (agentMode && !fc.isBlank()) processAgentMode(conversationId, fc, emitter);

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

    // =========================================================================
    // Pós-processamento
    // =========================================================================

    private void processCodeMode(UUID conversationId, String response, SseEmitter emitter) {
        try {
            var files = codeGenerationService.extractAndSaveFiles(conversationId, response);
            if (files.isEmpty()) return;

            String fileList = files.stream()
                    .map(f -> f.getFilePath() + "|" + f.getId()
                            + "|" + (f.isNewFile() ? "new" : "updated")
                            + "|" + f.getVersion())
                    .reduce((a, b) -> a + ";" + b)
                    .orElse("");

            emitter.send(SseEmitter.event().name("code-files").data(fileList));
        } catch (Exception e) {
            log.error("Erro no processamento do Modo Código", e);
        }
    }

    private void processAgentMode(UUID conversationId, String response, SseEmitter emitter) {
        try {
            var actions = agentService.extractAndPersistActions(conversationId, response);
            if (actions.isEmpty()) return;
            String json = objectMapper.writeValueAsString(actions);
            emitter.send(SseEmitter.event().name("agent-actions").data(json));
        } catch (Exception e) {
            log.error("Erro no processamento do Modo Agente", e);
        }
    }

    // =========================================================================
    // Montagem do payload
    // =========================================================================

    private Map<String, Object> buildPayload(String model,
                                              List<Message> history,
                                              Map<String, Object> options,
                                              String systemPrompt,
                                              List<String> images,
                                              UUID projectId,
                                              List<String> webContexts,
                                              boolean codeMode,
                                              boolean agentMode,
                                              String githubContext) {
        // System prompt em camadas
        String sp = IDENTITY_PROMPT;
        if (codeMode)  sp = merge(sp, codeGenerationService.buildCodeModeSystemPrompt(null));
        if (agentMode) sp = merge(sp, agentService.buildAgentModeSystemPrompt());
        sp = merge(sp, memoryService.buildMemorySystemPrompt());
        if (projectId != null) sp = merge(sp, projectService.buildProjectContext(projectId));
        sp = merge(sp, githubContext);
        sp = merge(sp, buildWebBlock(webContexts));
        sp = merge(sp, systemPrompt);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", sp != null ? sp : ""));

        for (int i = 0; i < history.size(); i++) {
            Message msg = history.get(i);
            if ("tool".equals(msg.getRole())) continue;

            boolean isLastUser = "user".equals(msg.getRole())
                    && i == history.size() - 1
                    && images != null && !images.isEmpty();

            if (isLastUser) {
                Map<String, Object> m = new HashMap<>(msg.toOllamaMap());
                m.put("images", images);
                messages.add(m);
            } else {
                messages.add(new HashMap<>(msg.toOllamaMap()));
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model",    model);
        payload.put("messages", messages);
        payload.put("stream",   true);
        if (options != null && !options.isEmpty()) payload.put("options", options);
        return payload;
    }

    private String buildWebBlock(List<String> contexts) {
        if (contexts == null || contexts.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("## Contexto de pesquisas na web\n\n");
        for (int i = 0; i < contexts.size(); i++) {
            if (contexts.size() > 1) sb.append("### Busca ").append(i + 1).append("\n");
            sb.append(contexts.get(i)).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String merge(String a, String b) {
        boolean ha = a != null && !a.isBlank();
        boolean hb = b != null && !b.isBlank();
        if (ha && hb) return a + "\n\n" + b;
        return ha ? a : (hb ? b : null);
    }

    // =========================================================================
    // Web search
    // =========================================================================

    private String fetchWebSearchContext(String query) {
        String r = fetchFromSearXNG(query);
        if (r != null) return r;
        r = fetchFromDuckDuckGoHTML(query);
        if (r != null) return r;
        return fetchFromDuckDuckGoInstant(query);
    }

    private String fetchFromSearXNG(String query) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(searxngUrl + "/search")
                    .queryParam("q", query)
                    .queryParam("format", "json")
                    .queryParam("language", "pt-BR")
                    .queryParam("categories", "general")
                    .build().toUriString();

            String body = httpClient.get().uri(url)
                    .header("User-Agent", "KyronAI/1.0")
                    .retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));

            if (body == null || body.isBlank()) return null;

            JsonNode results = objectMapper.readTree(body).path("results");
            if (!results.isArray() || results.isEmpty()) return null;

            StringBuilder ctx = new StringBuilder();
            ctx.append("**Resultados para:** ").append(query).append("\n\n");
            int count = 0;
            for (JsonNode r : results) {
                if (count >= 5) break;
                String title   = r.path("title").asText("").trim();
                String snippet = r.path("content").asText("").trim();
                String link    = r.path("url").asText("").trim();
                if (title.isBlank() && snippet.isBlank()) continue;
                ctx.append("**").append(count + 1).append(". ").append(title).append("**\n");
                if (!snippet.isBlank()) ctx.append(snippet).append("\n");
                if (!link.isBlank())    ctx.append("🔗 ").append(link).append("\n");
                ctx.append("\n");
                count++;
            }
            return count == 0 ? null : ctx.toString().trim();
        } catch (Exception e) {
            log.debug("SearXNG indisponível: {}", e.getMessage());
            return null;
        }
    }

    private String fetchFromDuckDuckGoHTML(String query) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://html.duckduckgo.com/html/")
                    .queryParam("q", query).queryParam("kl", "br-pt")
                    .build().toUriString();

            String body = httpClient.get().uri(url)
                    .header("User-Agent",
                            "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(12));

            if (body == null || body.isBlank()) return null;

            StringBuilder ctx = new StringBuilder();
            ctx.append("**Resultados para:** ").append(query).append("\n\n");
            String[] chunks = body.split("class=\"result__body\"");
            int count = 0;
            for (int i = 1; i < chunks.length && count < 5; i++) {
                String chunk   = chunks[i];
                String title   = between(chunk, "result__a\">", "</a>")
                        .replaceAll("<[^>]+>", "").trim();
                String snippet = between(chunk, "result__snippet\">", "</a>")
                        .replaceAll("<[^>]+>", "").trim();
                String link    = between(chunk, "result__url\">", "</a>")
                        .replaceAll("<[^>]+>", "").trim();
                if (title.isBlank() && snippet.isBlank()) continue;
                ctx.append("**").append(count + 1).append(". ").append(title).append("**\n");
                if (!snippet.isBlank()) ctx.append(snippet).append("\n");
                if (!link.isBlank())    ctx.append("🔗 ").append(link).append("\n");
                ctx.append("\n");
                count++;
            }
            return count == 0 ? null : ctx.toString().trim();
        } catch (Exception e) {
            log.debug("DDG HTML falhou: {}", e.getMessage());
            return null;
        }
    }

    private String fetchFromDuckDuckGoInstant(String query) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String url = UriComponentsBuilder
                        .fromHttpUrl("https://api.duckduckgo.com/")
                        .queryParam("q", query).queryParam("format", "json")
                        .queryParam("no_html", "1").queryParam("skip_disambig", "1")
                        .build().toUriString();

                String body = httpClient.get().uri(url)
                        .header("User-Agent", "KyronAI/1.0")
                        .retrieve()
                        .onStatus(s -> !s.is2xxSuccessful(), r -> r.createException())
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(12));

                if (body == null || body.isBlank()) return null;
                JsonNode root = objectMapper.readTree(body);

                StringBuilder ctx = new StringBuilder();
                String abs = root.path("Abstract").asText("").trim();
                if (!abs.isBlank())
                    ctx.append("**").append(root.path("AbstractSource").asText("Fonte"))
                       .append(":** ").append(abs).append("\n\n");
                String answer = root.path("Answer").asText("").trim();
                if (!answer.isBlank())
                    ctx.append("**Resposta direta:** ").append(answer).append("\n\n");

                String out = ctx.toString().trim();
                return out.isEmpty() ? null : out;
            } catch (Exception e) {
                if (attempt < 2) {
                    try { Thread.sleep(500); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        return null;
    }

    // =========================================================================
    // Modelos
    // =========================================================================

    public Mono<Map<String, Object>> listModels() {
        return ollamaWebClient.get().uri("/api/tags")
                .retrieve().bodyToMono(String.class)
                .map(json -> {
                    try {
                        //noinspection unchecked
                        return (Map<String, Object>) objectMapper.readValue(json, Map.class);
                    } catch (Exception e) {
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
                    try {
                        //noinspection unchecked
                        return (Map<String, Object>) objectMapper.readValue(json, Map.class);
                    } catch (Exception e) {
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

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<String> extractWebContexts(List<Message> history) {
        List<String> ctxs = new ArrayList<>();
        for (Message m : history) {
            if ("tool".equals(m.getRole())
                    && m.getContent() != null
                    && m.getContent().startsWith(WEB_CONTEXT_PREFIX))
                ctxs.add(m.getContent().substring(WEB_CONTEXT_PREFIX.length()));
        }
        return ctxs;
    }

    private String lastUserMessage(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--)
            if ("user".equals(history.get(i).getRole())) return history.get(i).getContent();
        return "";
    }

    private boolean isFollowUp(String msg) {
        String lower = msg.toLowerCase().trim();
        return List.of("sim", "não", "ok", "certo", "entendi", "obrigado",
                       "obrigada", "continue", "continua", "pode elaborar",
                       "explica melhor", "me conta mais")
                .stream().anyMatch(lower::contains);
    }

    private String between(String text, String start, String end) {
        int s = text.indexOf(start);
        if (s == -1) return "";
        s += start.length();
        int e = text.indexOf(end, s);
        return e == -1 ? "" : text.substring(s, e);
    }

    private ModelCapabilities parseCapabilities(String modelName, String json) {
        boolean supportsThinking = false, supportsVision = false;
        int contextLength = 0;
        String family = "", parameterSize = "";
        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode caps = root.path("capabilities");
            if (caps.isArray()) {
                for (JsonNode cap : caps) {
                    String c = cap.asText().toLowerCase();
                    if (c.contains("think"))              supportsThinking = true;
                    if (c.contains("vision") || c.contains("image")) supportsVision = true;
                }
            }

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
                contextLength = details.path("context_length").asInt(0);
            }

            JsonNode modelInfo = root.path("model_info");
            if (!modelInfo.isMissingNode() && contextLength == 0) {
                for (String field : List.of("context_length", "num_ctx", "max_position_embeddings")) {
                    JsonNode n = modelInfo.path(field);
                    if (!n.isMissingNode() && n.isInt()) { contextLength = n.asInt(0); break; }
                }
            }

            String nl = modelName.toLowerCase();
            if (!supportsThinking)
                supportsThinking = THINKING_FAMILIES.stream().anyMatch(nl::contains)
                        || nl.contains("r1") || nl.contains("qwq");
            if (!supportsVision)
                supportsVision = VISION_FAMILIES.stream().anyMatch(nl::contains);
            if (contextLength == 0)
                contextLength = inferContextLength(nl);

        } catch (Exception e) {
            log.warn("Erro ao parsear capabilities '{}': {}", modelName, e.getMessage());
        }
        return ModelCapabilities.builder()
                .modelName(modelName).supportsThinking(supportsThinking)
                .supportsVision(supportsVision).contextLength(contextLength)
                .family(family).parameterSize(parameterSize).build();
    }

    private ModelCapabilities buildFallbackCapabilities(String modelName) {
        String nl = modelName.toLowerCase();
        return ModelCapabilities.builder()
                .modelName(modelName)
                .supportsThinking(THINKING_FAMILIES.stream().anyMatch(nl::contains)
                        || nl.contains("r1") || nl.contains("qwq"))
                .supportsVision(VISION_FAMILIES.stream().anyMatch(nl::contains))
                .contextLength(inferContextLength(nl))
                .family("").parameterSize("").build();
    }

    private int inferContextLength(String n) {
        if (n.contains("minimax"))  return 1_000_000;
        if (n.contains("kimi"))     return 131_072;
        if (n.contains("qwen3"))    return 32_768;
        if (n.contains("nemotron")) return 32_768;
        return 4_096;
    }
}