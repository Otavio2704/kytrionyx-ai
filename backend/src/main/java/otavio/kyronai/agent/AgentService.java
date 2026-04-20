package otavio.kyronai.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import otavio.kyronai.chat.Conversation;
import otavio.kyronai.chat.ConversationRepository;
import otavio.kyronai.code.CodeGenerationService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentActionRepository  actionRepository;
    private final ConversationRepository conversationRepository;
    private final CodeGenerationService  codeGenerationService;

    private static final Pattern ACTION_BLOCK = Pattern.compile(
            "\\[ACTION:([A-Z_]+)\\]\\n(.*?)\\[/ACTION\\]", Pattern.DOTALL);

    private static final Pattern ACTION_FIELD = Pattern.compile(
            "^(path|description):\\s*(.+)$", Pattern.MULTILINE);

    // ── Parse e persistência ─────────────────────────────────────────────────

    @Transactional
    public List<AgentActionDTO> extractAndPersistActions(UUID conversationId,
                                                          String aiResponse) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversa não encontrada: " + conversationId));

        List<AgentAction> actions = new ArrayList<>();
        Matcher m = ACTION_BLOCK.matcher(aiResponse);
        int order = 0;

        while (m.find()) {
            try {
                AgentAction.ActionType type =
                        AgentAction.ActionType.valueOf(m.group(1));
                String body = m.group(2);
                Map<String, String> fields = parseFields(body);

                actions.add(actionRepository.save(AgentAction.builder()
                        .conversation(conv).actionType(type)
                        .filePath(fields.get("path"))
                        .description(fields.get("description"))
                        .proposedContent(extractContent(body))
                        .status(AgentAction.ActionStatus.PENDING)
                        .executionOrder(order++).build()));
            } catch (IllegalArgumentException e) {
                log.warn("Tipo de ação desconhecido: {}", m.group(1));
            }
        }

        if (actions.isEmpty())
            actions.addAll(inferFromCodeBlocks(conv, aiResponse, order));

        return actions.stream().map(AgentActionDTO::fromEntity).toList();
    }

    @Transactional
    public AgentActionDTO approveAction(UUID actionId) {
        AgentAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ação não encontrada: " + actionId));

        if (action.getStatus() != AgentAction.ActionStatus.PENDING)
            throw new IllegalStateException("Ação não está pendente: " + action.getStatus());

        action.setStatus(AgentAction.ActionStatus.APPROVED);
        actionRepository.save(action);

        try {
            execute(action);
            action.setStatus(AgentAction.ActionStatus.EXECUTED);
        } catch (Exception e) {
            action.setStatus(AgentAction.ActionStatus.FAILED);
            log.error("Falha ao executar ação {}", actionId, e);
        }
        action.setResolvedAt(LocalDateTime.now());
        return AgentActionDTO.fromEntity(actionRepository.save(action));
    }

    @Transactional
    public AgentActionDTO rejectAction(UUID actionId) {
        AgentAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ação não encontrada: " + actionId));
        action.setStatus(AgentAction.ActionStatus.REJECTED);
        action.setResolvedAt(LocalDateTime.now());
        return AgentActionDTO.fromEntity(actionRepository.save(action));
    }

    @Transactional(readOnly = true)
    public List<AgentActionDTO> getActionsByConversation(UUID conversationId) {
        return actionRepository
                .findByConversationIdOrderByExecutionOrderAsc(conversationId)
                .stream().map(AgentActionDTO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public long countPendingActions(UUID conversationId) {
        return actionRepository.countByConversationIdAndStatus(
                conversationId, AgentAction.ActionStatus.PENDING);
    }

    public String buildAgentModeSystemPrompt() {
        return """
            ## Modo Agente Ativo

            Declare EXPLICITAMENTE as ações antes de gerar código:

            [ACTION:CREATE_FILE]
            path: src/main/java/App.java
            description: Descrição da ação
```java:src/main/java/App.java
            // conteúdo
```
            [/ACTION]

            Tipos: CREATE_FILE | EDIT_FILE | DELETE_FILE | EXPLAIN
            O usuário aprovará cada ação individualmente.
            """;
    }

    // ── Execução ─────────────────────────────────────────────────────────────

    private void execute(AgentAction action) {
        switch (action.getActionType()) {
            case CREATE_FILE, EDIT_FILE -> {
                if (action.getFilePath() != null && action.getProposedContent() != null) {
                    String ext = ext(action.getFilePath());
                    String block = "```" + ext + ":" + action.getFilePath()
                            + "\n" + action.getProposedContent() + "\n```";
                    codeGenerationService.extractAndSaveFiles(
                            action.getConversation().getId(), block);
                }
            }
            case DELETE_FILE -> log.info("DELETE marcado: {}", action.getFilePath());
            case EXPLAIN     -> log.info("EXPLAIN registrado");
            default          -> log.warn("Tipo não implementado: {}", action.getActionType());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<AgentAction> inferFromCodeBlocks(Conversation conv,
                                                   String response, int startOrder) {
        List<AgentAction> list = new ArrayList<>();
        Pattern p = Pattern.compile(
                "```([a-zA-Z0-9+#_-]*)(?::([^\\n`]+))?\\n([\\s\\S]*?)```",
                Pattern.MULTILINE);
        Matcher m = p.matcher(response);
        int order = startOrder;
        while (m.find()) {
            String path    = m.group(2);
            String content = m.group(3);
            if (path == null || path.isBlank() || content == null) continue;
            list.add(actionRepository.save(AgentAction.builder()
                    .conversation(conv)
                    .actionType(AgentAction.ActionType.CREATE_FILE)
                    .filePath(path.trim())
                    .description("Criar " + path.trim())
                    .proposedContent(content)
                    .status(AgentAction.ActionStatus.PENDING)
                    .executionOrder(order++).build()));
        }
        return list;
    }

    private Map<String, String> parseFields(String body) {
        Map<String, String> map = new HashMap<>();
        Matcher m = ACTION_FIELD.matcher(body);
        while (m.find()) map.put(m.group(1).trim(), m.group(2).trim());
        return map;
    }

    private String extractContent(String body) {
        Matcher m = Pattern.compile("```[^\\n]*\\n([\\s\\S]*?)```").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private String ext(String path) {
        int i = path.lastIndexOf('.');
        return i >= 0 ? path.substring(i + 1).toLowerCase() : "txt";
    }
}