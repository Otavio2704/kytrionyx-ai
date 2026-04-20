package otavio.kyronai.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public UUID createConversation(String title, String modelName) {
        Conversation c = Conversation.builder()
                .title(title).modelName(modelName).build();
        UUID id = conversationRepository.save(c).getId();
        log.info("Conversa criada: id={} modelo={}", id, modelName);
        return id;
    }

    @Transactional
    public UUID createConversationForProject(String title, String modelName, UUID projectId) {
        Conversation c = Conversation.builder()
                .title(title).modelName(modelName).projectId(projectId).build();
        UUID id = conversationRepository.save(c).getId();
        log.info("Conversa criada para projeto {}: id={}", projectId, id);
        return id;
    }

    @Transactional(readOnly = true)
    public List<Conversation> getAllConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Conversation> getConversationById(UUID id) {
        return conversationRepository.findById(id).map(c -> {
            c.getMessages().size(); // força load LAZY
            return c;
        });
    }

    @Transactional(readOnly = true)
    public List<Conversation> getConversationsByProject(UUID projectId) {
        return conversationRepository.findByProjectIdOrderByUpdatedAtDesc(projectId);
    }

    @Transactional
    public boolean deleteConversation(UUID id) {
        if (!conversationRepository.existsById(id)) return false;
        conversationRepository.deleteById(id);
        return true;
    }

    @Transactional
    public boolean togglePin(UUID id) {
        return conversationRepository.findById(id).map(c -> {
            if (!c.isPinned()) {
                long count = conversationRepository.findAllByOrderByUpdatedAtDesc()
                        .stream().filter(Conversation::isPinned).count();
                if (count >= 3) return false;
            }
            c.setPinned(!c.isPinned());
            conversationRepository.save(c);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean renameConversation(UUID id, String newTitle) {
        return conversationRepository.findById(id).map(c -> {
            c.setTitle(newTitle);
            conversationRepository.save(c);
            return true;
        }).orElse(false);
    }

    @Transactional
    public Message addMessage(UUID conversationId, String role, String content) {
        return addMessage(conversationId, role, content, false);
    }

    @Transactional
    public Message addMessage(UUID conversationId, String role,
                               String content, boolean thinkingEnabled) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversa não encontrada: " + conversationId));
        Message msg = new Message(c, role, content, thinkingEnabled);
        Message saved = messageRepository.save(msg);
        conversationRepository.save(c); // atualiza updatedAt
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Message> getMessages(UUID conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional(readOnly = true)
    public List<Message> getLastMessages(UUID conversationId, int limit) {
        return messageRepository.findLastNByConversationId(conversationId, limit);
    }
}