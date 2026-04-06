package otavio.openchat.controller;

import otavio.openchat.model.Memory;
import otavio.openchat.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    /** GET /api/memories — lista todas as memórias */
    @GetMapping
    public ResponseEntity<List<Memory>> listMemories() {
        return ResponseEntity.ok(memoryService.getAllMemories());
    }

    /** POST /api/memories — cria uma nova memória */
    @PostMapping
    public ResponseEntity<Memory> createMemory(@RequestBody Map<String, String> body) {
        String content  = body.get("content");
        String category = body.getOrDefault("category", "geral");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(memoryService.createMemory(content, category));
    }

    /** PATCH /api/memories/{id}/toggle — ativa ou desativa */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Memory> toggleMemory(@PathVariable UUID id) {
        return memoryService.toggleMemory(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** PUT /api/memories/{id} — atualiza conteúdo */
    @PutMapping("/{id}")
    public ResponseEntity<Memory> updateMemory(@PathVariable UUID id,
                                                @RequestBody Map<String, String> body) {
        return memoryService.updateMemory(id, body.get("content"), body.get("category"))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** DELETE /api/memories/{id} — deleta permanentemente */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMemory(@PathVariable UUID id) {
        return memoryService.deleteMemory(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}