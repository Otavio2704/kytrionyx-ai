package otavio.kyronai.models;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import otavio.kyronai.ollama.OllamaService;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final OllamaService ollamaService;

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> list() {
        return ollamaService.listModels()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body(Map.of("error", e.getMessage()))));
    }

    @GetMapping("/{name}/info")
    public Mono<ResponseEntity<Map<String, Object>>> info(@PathVariable String name) {
        return ollamaService.getModelInfo(name)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body(Map.of("error", e.getMessage()))));
    }

    @GetMapping("/{name}/capabilities")
    public Mono<ResponseEntity<ModelCapabilities>> capabilities(@PathVariable String name) {
        return ollamaService.getModelCapabilities(name)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }
}