package otavio.openchat.service;

import otavio.openchat.model.Conversation;
import otavio.openchat.model.Message;
import otavio.openchat.repository.ConversationRepository;
import otavio.openchat.repository.MessageRepository;
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

    // -------------------------------------------------------------------------
    // Conversas
    // -------------------------------------------------------------------------

    /**
     * Cria uma nova conversa vazia com título e modelo definidos.
     *
     * @param title     Título gerado a partir dos primeiros 50 chars da 1ª mensagem
     * @param modelName Nome do modelo Ollama usado (ex: llama3.2, mistral)
     * @return UUID da conversa criada
     */
    @Transactional
    public UUID createConversation(String title, String modelName) {
        Conversation conversation = Conversation.builder()
                .title(title)
                .modelName(modelName)
                .build();

        Conversation saved = conversationRepository.save(conversation);
        log.info("Nova conversa criada: id={}, modelo={}", saved.getId(), modelName);
        return saved.getId();
    }

    /**
     * Lista todas as conversas sem carregar mensagens (para a sidebar).
     * Ordenadas da mais recente para a mais antiga.
     */
    @Transactional(readOnly = true)
    public List<Conversation> getAllConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc();
    }

    /**
     * Busca uma conversa pelo ID, incluindo suas mensagens (fetch LAZY ativado aqui).
     *
     * @param id UUID da conversa
     * @return Optional com a conversa e mensagens, ou empty se não encontrada
     */
    @Transactional(readOnly = true)
    public Optional<Conversation> getConversationById(UUID id) {
        return conversationRepository.findById(id).map(conversation -> {
            // Força o carregamento LAZY das mensagens dentro da transação
            conversation.getMessages().size();
            return conversation;
        });
    }

    /**
     * Deleta uma conversa e todas as suas mensagens (via CascadeType.ALL).
     *
     * @param id UUID da conversa
     * @return true se deletada com sucesso, false se não encontrada
     */
    @Transactional
    public boolean deleteConversation(UUID id) {
        if (!conversationRepository.existsById(id)) {
            return false;
        }
        conversationRepository.deleteById(id);
        log.info("Conversa {} deletada", id);
        return true;
    }

    // -------------------------------------------------------------------------
    // Mensagens
    // -------------------------------------------------------------------------

    /**
     * Alterna o estado de pin de uma conversa.
     * Máximo de 3 conversas fixadas — retorna false se limite atingido.
     */
    @Transactional
    public boolean togglePin(UUID id) {
        return conversationRepository.findById(id).map(c -> {
            if (!c.isPinned()) {
                // Conta quantas já estão fixadas
                long pinnedCount = conversationRepository.findAllByOrderByUpdatedAtDesc()
                        .stream().filter(Conversation::isPinned).count();
                if (pinnedCount >= 3) return false;
            }
            c.setPinned(!c.isPinned());
            conversationRepository.save(c);
            return true;
        }).orElse(false);
    }

    /**
     * Renomeia o título de uma conversa existente.
     */
    @Transactional
    public boolean renameConversation(UUID id, String newTitle) {
        return conversationRepository.findById(id).map(c -> {
            c.setTitle(newTitle);
            conversationRepository.save(c);
            return true;
        }).orElse(false);
    }

    /**
     * Adiciona uma mensagem a uma conversa existente e atualiza o updated_at da conversa.
     *
     * @param conversationId UUID da conversa
     * @param role           "user" ou "assistant"
     * @param content        Conteúdo da mensagem
     * @return A mensagem salva
     * @throws IllegalArgumentException se a conversa não for encontrada
     */
    @Transactional
    public Message addMessage(UUID conversationId, String role, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversa não encontrada: " + conversationId));

        Message message = new Message(conversation, role, content);
        Message saved = messageRepository.save(message);

        // Salva a conversa novamente para disparar o @UpdateTimestamp
        conversationRepository.save(conversation);

        log.debug("Mensagem salva: conversationId={}, role={}, tamanho={}",
                conversationId, role, content.length());
        return saved;
    }

    /**
     * Retorna todas as mensagens de uma conversa em ordem cronológica.
     * Usado para montar o histórico de contexto enviado ao Ollama.
     *
     * @param conversationId UUID da conversa
     */
    @Transactional(readOnly = true)
    public List<Message> getMessages(UUID conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /**
     * Retorna as últimas N mensagens de uma conversa para limitar o contexto.
     * Útil quando a conversa é muito longa e estoura o num_ctx do modelo.
     *
     * @param conversationId UUID da conversa
     * @param limit          Número máximo de mensagens a retornar
     */
    @Transactional(readOnly = true)
    public List<Message> getLastMessages(UUID conversationId, int limit) {
        return messageRepository.findLastNByConversationId(conversationId, limit);
    }
}