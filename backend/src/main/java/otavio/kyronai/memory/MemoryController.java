package otavio.kyronai.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    @GetMapping
    public ResponseEntity<List<Memory>> list() {
        return ResponseEntity.ok(memoryService.getAllMemories());
    }

    @PostMapping
    public ResponseEntity<Memory> create(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(
                memoryService.createMemory(content, body.getOrDefault("category", "geral")));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Memory> toggle(@PathVariable UUID id) {
        return memoryService.toggleMemory(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Memory> update(@PathVariable UUID id,
                                          @RequestBody Map<String, String> body) {
        return memoryService.updateMemory(id, body.get("content"), body.get("category"))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return memoryService.deleteMemory(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}