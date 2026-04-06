package otavio.openchat.service;

import otavio.openchat.model.Memory;
import otavio.openchat.repository.MemoryRepository;
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

    /**
     * Retorna todas as memórias ativas formatadas como um bloco de system prompt.
     * Injetado automaticamente em cada requisição ao Ollama.
     *
     * Exemplo de output:
     * "O usuário tem as seguintes preferências e contexto:
     * - Prefere respostas em português
     * - Trabalha com Java e Spring Boot
     * - Está construindo um chatbot chamado OpenChat"
     */
    @Transactional(readOnly = true)
    public String buildMemorySystemPrompt() {
        List<Memory> memories = memoryRepository.findByActiveTrueOrderByCreatedAtDesc();
        if (memories.isEmpty()) return null;

        String facts = memories.stream()
                .map(m -> "- " + m.getContent())
                .collect(Collectors.joining("\n"));

        return "O usuário tem as seguintes preferências e contexto:\n" + facts;
    }

    /** Lista todas as memórias para exibição na interface */
    @Transactional(readOnly = true)
    public List<Memory> getAllMemories() {
        return memoryRepository.findAllByOrderByCreatedAtDesc();
    }

    /** Cria uma nova memória */
    @Transactional
    public Memory createMemory(String content, String category) {
        Memory memory = Memory.builder()
                .content(content)
                .category(category)
                .active(true)
                .build();
        Memory saved = memoryRepository.save(memory);
        log.info("Memória criada: {}", content);
        return saved;
    }

    /** Ativa ou desativa uma memória sem deletá-la */
    @Transactional
    public Optional<Memory> toggleMemory(UUID id) {
        return memoryRepository.findById(id).map(m -> {
            m.setActive(!m.isActive());
            return memoryRepository.save(m);
        });
    }

    /** Deleta uma memória permanentemente */
    @Transactional
    public boolean deleteMemory(UUID id) {
        if (!memoryRepository.existsById(id)) return false;
        memoryRepository.deleteById(id);
        log.info("Memória {} deletada", id);
        return true;
    }

    /** Atualiza o conteúdo de uma memória */
    @Transactional
    public Optional<Memory> updateMemory(UUID id, String content, String category) {
        return memoryRepository.findById(id).map(m -> {
            m.setContent(content);
            if (category != null) m.setCategory(category);
            return memoryRepository.save(m);
        });
    }
}