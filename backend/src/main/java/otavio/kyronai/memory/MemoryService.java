package otavio.kyronai.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryRepository memoryRepository;

    @Transactional(readOnly = true)
    public String buildMemorySystemPrompt() {
        List<Memory> memories = memoryRepository.findByActiveTrueOrderByCreatedAtDesc();
        if (memories.isEmpty()) return null;
        String facts = memories.stream()
                .map(m -> "- " + m.getContent())
                .collect(Collectors.joining("\n"));
        return "O usuário tem as seguintes preferências e contexto:\n" + facts;
    }

    @Transactional(readOnly = true)
    public List<Memory> getAllMemories() {
        return memoryRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Memory createMemory(String content, String category) {
        return memoryRepository.save(Memory.builder()
                .content(content).category(category).active(true).build());
    }

    @Transactional
    public Optional<Memory> toggleMemory(UUID id) {
        return memoryRepository.findById(id).map(m -> {
            m.setActive(!m.isActive());
            return memoryRepository.save(m);
        });
    }

    @Transactional
    public Optional<Memory> updateMemory(UUID id, String content, String category) {
        return memoryRepository.findById(id).map(m -> {
            if (content  != null) m.setContent(content);
            if (category != null) m.setCategory(category);
            return memoryRepository.save(m);
        });
    }

    @Transactional
    public boolean deleteMemory(UUID id) {
        if (!memoryRepository.existsById(id)) return false;
        memoryRepository.deleteById(id);
        return true;
    }
}