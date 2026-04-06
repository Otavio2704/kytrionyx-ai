package otavio.openchat.controller;

import otavio.openchat.model.ModelCapabilities;
import otavio.openchat.service.OllamaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final OllamaService ollamaService;

    /**
     * GET /api/models
     * Lista todos os modelos disponíveis no Ollama local.
     */
    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> listModels() {
        return ollamaService.listModels()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Erro ao listar modelos do Ollama", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(Map.of("error", "Não foi possível conectar ao Ollama: " + e.getMessage())));
                });
    }

    /**
     * GET /api/models/{name}/info
     * Retorna detalhes brutos de um modelo específico via /api/show do Ollama.
     */
    @GetMapping("/{name}/info")
    public Mono<ResponseEntity<Map<String, Object>>> getModelInfo(@PathVariable String name) {
        return ollamaService.getModelInfo(name)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Erro ao buscar informações do modelo '{}'", name, e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(Map.of("error", "Modelo não encontrado ou Ollama indisponível: " + e.getMessage())));
                });
    }

    /**
     * GET /api/models/{name}/capabilities
     * Retorna as capabilities detectadas de um modelo:
     * - supportsThinking: se o modelo suporta thinking mode
     * - supportsVision:   se o modelo aceita imagens
     * - contextLength:    tamanho máximo do contexto em tokens
     * - acceptedFileTypes: lista de extensões aceitas
     *
     * Usado pelo frontend para habilitar/desabilitar botões da UI dinamicamente.
     */
    @GetMapping("/{name}/capabilities")
    public Mono<ResponseEntity<ModelCapabilities>> getModelCapabilities(@PathVariable String name) {
        return ollamaService.getModelCapabilities(name)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Erro ao detectar capabilities do modelo '{}'", name, e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }
}