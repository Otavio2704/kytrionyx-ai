package otavio.kyronai.code;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import otavio.kyronai.chat.Conversation;
import otavio.kyronai.chat.ConversationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGenerationService {

    private final CodeSessionRepository   sessionRepository;
    private final GeneratedFileRepository fileRepository;
    private final ConversationRepository  conversationRepository;

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```([a-zA-Z0-9+#_-]*)(?::([^\\n`]+))?\\n([\\s\\S]*?)```",
            Pattern.MULTILINE);

    @Transactional
    public List<GeneratedFile> extractAndSaveFiles(UUID conversationId, String aiResponse) {
        List<ParsedBlock> blocks = parse(aiResponse);
        if (blocks.isEmpty()) return List.of();

        CodeSession session = sessionRepository.findByConversationId(conversationId)
                .orElseGet(() -> {
                    Conversation conv = conversationRepository.findById(conversationId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Conversa não encontrada: " + conversationId));
                    return sessionRepository.save(CodeSession.builder()
                            .conversation(conv).title("Sessão de Código").build());
                });

        if (session.getPrimaryLanguage() == null) {
            blocks.stream().map(ParsedBlock::language)
                  .filter(l -> l != null && !l.isBlank()).findFirst()
                  .ifPresent(lang -> {
                      session.setPrimaryLanguage(lang);
                      sessionRepository.save(session);
                  });
        }

        List<GeneratedFile> saved = new ArrayList<>();
        for (ParsedBlock block : blocks) {
            if (block.filePath() == null || block.filePath().isBlank()) continue;

            String path  = sanitize(block.filePath());
            String name  = fileName(path);
            String ext   = extension(name);

            Optional<GeneratedFile> existing =
                    fileRepository.findByCodeSessionIdAndFilePath(session.getId(), path);

            GeneratedFile file;
            if (existing.isPresent()) {
                GeneratedFile gf = existing.get();
                gf.setPreviousContent(gf.getContent());
                gf.setContent(block.content());
                gf.setVersion(gf.getVersion() + 1);
                gf.setNewFile(false);
                file = fileRepository.save(gf);
            } else {
                file = fileRepository.save(GeneratedFile.builder()
                        .codeSession(session).filePath(path)
                        .fileName(name).extension(ext)
                        .content(block.content()).version(1).newFile(true).build());
            }
            saved.add(file);
        }
        sessionRepository.save(session);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<CodeSessionDTO> getSessionByConversation(UUID conversationId) {
        return sessionRepository.findByConversationId(conversationId)
                .map(s -> { s.getFiles().size(); return CodeSessionDTO.fromEntity(s); });
    }

    @Transactional(readOnly = true)
    public Optional<CodeSessionDTO> getSessionById(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(s -> { s.getFiles().size(); return CodeSessionDTO.fromEntity(s); });
    }

    @Transactional
    public void clearSession(UUID sessionId) {
        fileRepository.deleteByCodeSessionId(sessionId);
    }

    public String buildCodeModeSystemPrompt(String language) {
        return """
            ## Modo Código Ativo

            Ao gerar código, SEMPRE use o formato abaixo para que os arquivos \
            sejam detectados automaticamente:

```linguagem:caminho/do/arquivo.ext
            conteúdo aqui
```

            Regras:
            - Inclua o caminho completo após a linguagem (ex: ```java:src/main/App.java)
            - Use caminhos relativos à raiz do projeto
            - Ao editar um arquivo existente, use o mesmo caminho
            - Gere código completo — nunca truncado
            """ + (language != null && !language.isBlank()
                ? "\nLinguagem principal: " + language : "");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<ParsedBlock> parse(String text) {
        List<ParsedBlock> blocks = new ArrayList<>();
        if (text == null || text.isBlank()) return blocks;
        Matcher m = CODE_BLOCK_PATTERN.matcher(text);
        while (m.find()) {
            String lang    = m.group(1);
            String path    = m.group(2);
            String content = m.group(3);
            if (content == null) continue;
            content = content.stripTrailing();
            if ((path == null || path.isBlank()) && lang != null)
                path = inferPath(lang, content);
            blocks.add(new ParsedBlock(lang, path, content));
        }
        return blocks;
    }

    private String inferPath(String lang, String content) {
        return switch (lang.toLowerCase()) {
            case "java" -> {
                Matcher pkg = Pattern.compile("^package\\s+([\\w.]+);",
                        Pattern.MULTILINE).matcher(content);
                Matcher cls = Pattern.compile(
                        "(?:public\\s+)?(?:class|interface|enum|record)\\s+(\\w+)")
                        .matcher(content);
                yield (pkg.find() && cls.find())
                        ? "src/main/java/" + pkg.group(1).replace('.', '/')
                          + "/" + cls.group(1) + ".java"
                        : null;
            }
            case "html"                 -> "index.html";
            case "css"                  -> "style.css";
            case "javascript", "js"     -> "script.js";
            case "typescript", "ts"     -> "index.ts";
            default                     -> null;
        };
    }

    private String sanitize(String path) {
        return path.replaceAll("^\\.?/+", "").replaceAll("\\\\", "/").trim();
    }

    private String fileName(String path) {
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private String extension(String name) {
        int i = name.lastIndexOf('.');
        return (i >= 0 && i < name.length() - 1)
                ? name.substring(i + 1).toLowerCase() : "";
    }

    private record ParsedBlock(String language, String filePath, String content) {}
}