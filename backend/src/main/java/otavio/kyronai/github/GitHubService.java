package otavio.kyronai.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private final GitHubRepoStore repoStore;
    private final ObjectMapper    objectMapper;

    private static final String GITHUB_API          = "https://api.github.com";
    private static final int    MAX_FILE_SIZE_BYTES  = 50_000;
    private static final int    MAX_TOTAL_CONTEXT    = 80_000;
    private static final int    MAX_FILES_PER_REPO   = 100;

    private static final Set<String> INDEXABLE_EXTS = Set.of(
        "java", "kt", "py", "rb", "js", "ts", "jsx", "tsx", "go", "rs",
        "c", "cpp", "cs", "swift", "dart", "sql", "yaml", "yml", "json",
        "toml", "xml", "md", "html", "css", "scss", "sh", "dockerfile"
    );

    private static final Set<String> IGNORED_DIRS = Set.of(
        "node_modules", ".git", "vendor", "target", "build",
        "dist", ".idea", ".vscode", "__pycache__", ".gradle"
    );

    private final WebClient githubClient = WebClient.builder()
            .baseUrl(GITHUB_API)
            .defaultHeader("Accept", "application/vnd.github.v3+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .build();

    private final ExecutorService indexingExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "github-indexer");
        t.setDaemon(true);
        return t;
    });

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GitHubRepoDTO> getAllRepos() {
        return repoStore.findAllByOrderByCreatedAtDesc().stream()
                .map(GitHubRepoDTO::fromEntity).toList();
    }

    @Transactional
    public GitHubRepoDTO connectRepo(String fullName, String branch,
                                      String accessToken, boolean isPrivate) {
        if (!fullName.matches("[\\w.-]+/[\\w.-]+"))
            throw new IllegalArgumentException("Formato inválido. Use: owner/repositório");

        String[] parts = fullName.split("/", 2);

        GitHubRepo repo = repoStore.findByFullName(fullName)
                .orElseGet(() -> GitHubRepo.builder()
                        .fullName(fullName).owner(parts[0]).repoName(parts[1]).build());

        repo.setBranch(branch != null && !branch.isBlank() ? branch : "main");
        repo.setAccessToken(accessToken != null && !accessToken.isBlank() ? accessToken : null);
        repo.setIsPrivate(isPrivate);
        repo.setIndexStatus(GitHubRepo.IndexStatus.PENDING);

        GitHubRepo saved = repoStore.save(repo);
        indexingExecutor.execute(() -> indexRepo(saved.getId()));
        return GitHubRepoDTO.fromEntity(saved);
    }

    @Transactional
    public boolean deleteRepo(UUID id) {
        if (!repoStore.existsById(id)) return false;
        repoStore.deleteById(id);
        return true;
    }

    @Transactional
    public GitHubRepoDTO reindex(UUID id) {
        GitHubRepo repo = repoStore.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Repositório não encontrado: " + id));
        repo.setIndexStatus(GitHubRepo.IndexStatus.PENDING);
        repo.setContextIndex(null);
        repoStore.save(repo);
        indexingExecutor.execute(() -> indexRepo(id));
        return GitHubRepoDTO.fromEntity(repo);
    }

    // ── Indexação ─────────────────────────────────────────────────────────────

    public void indexRepo(UUID repoId) {
        try {
            GitHubRepo repo = repoStore.findById(repoId).orElse(null);
            if (repo == null) return;

            updateStatus(repoId, GitHubRepo.IndexStatus.INDEXING);
            List<RepoFile> files = fetchTree(repo);

            if (files.isEmpty()) {
                updateStatusAndCount(repoId, GitHubRepo.IndexStatus.READY, 0, null);
                return;
            }

            StringBuilder index = new StringBuilder();
            index.append("# Repositório: ").append(repo.getFullName())
                 .append(" (branch: ").append(repo.getBranch()).append(")\n\n");

            int totalChars = 0, indexed = 0;
            for (RepoFile file : files) {
                if (indexed >= MAX_FILES_PER_REPO || totalChars >= MAX_TOTAL_CONTEXT) break;
                String content = fetchContent(repo, file.path());
                if (content == null || content.isBlank()) continue;
                if (content.length() > MAX_FILE_SIZE_BYTES)
                    content = content.substring(0, MAX_FILE_SIZE_BYTES) + "\n[... truncado ...]";
                index.append("## ").append(file.path()).append("\n```")
                     .append(file.ext()).append("\n").append(content).append("\n```\n\n");
                totalChars += content.length();
                indexed++;
            }
            updateStatusAndCount(repoId, GitHubRepo.IndexStatus.READY, indexed, index.toString());
            log.info("Indexação concluída: {} — {} arquivos", repo.getFullName(), indexed);

        } catch (Exception e) {
            log.error("Falha na indexação {}: {}", repoId, e.getMessage(), e);
            updateStatus(repoId, GitHubRepo.IndexStatus.ERROR);
        }
    }

    @Transactional(readOnly = true)
    public String buildContext(UUID repoId) {
        return repoStore.findById(repoId)
                .filter(r -> r.getIndexStatus() == GitHubRepo.IndexStatus.READY)
                .map(r -> "## Contexto GitHub: " + r.getFullName() + "\n\n"
                        + "Branch: " + r.getBranch() + "\n\n"
                        + Optional.ofNullable(r.getContextIndex()).orElse(""))
                .orElse(null);
    }

    // ── GitHub API ────────────────────────────────────────────────────────────

    private List<RepoFile> fetchTree(GitHubRepo repo) {
        try {
            String body = githubClient.get()
                    .uri("/repos/" + repo.getFullName()
                            + "/git/trees/" + repo.getBranch() + "?recursive=1")
                    .headers(h -> auth(h, repo.getAccessToken()))
                    .retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));

            if (body == null) return List.of();
            JsonNode tree = objectMapper.readTree(body).path("tree");
            if (!tree.isArray()) return List.of();

            List<RepoFile> files = new ArrayList<>();
            for (JsonNode node : tree) {
                if (!"blob".equals(node.path("type").asText())) continue;
                String path = node.path("path").asText();
                long   size = node.path("size").asLong(0);
                if (size > MAX_FILE_SIZE_BYTES || isIgnored(path)) continue;
                String ext = ext(path);
                if (!INDEXABLE_EXTS.contains(ext)) continue;
                files.add(new RepoFile(path, ext, size));
            }
            files.sort(Comparator
                    .comparingInt((RepoFile f) -> isHighPriority(f.path()) ? 0 : 1)
                    .thenComparingLong(RepoFile::size));
            return files;

        } catch (WebClientResponseException e) {
            log.error("GitHub API error {}: {}", repo.getFullName(), e.getStatusCode());
            return List.of();
        } catch (Exception e) {
            log.error("Erro ao buscar tree {}: {}", repo.getFullName(), e.getMessage());
            return List.of();
        }
    }

    private String fetchContent(GitHubRepo repo, String path) {
        try {
            String body = githubClient.get()
                    .uri("/repos/" + repo.getFullName() + "/contents/" + path
                            + "?ref=" + repo.getBranch())
                    .headers(h -> auth(h, repo.getAccessToken()))
                    .retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));
            if (body == null) return null;
            JsonNode node = objectMapper.readTree(body);
            String content  = node.path("content").asText("");
            String encoding = node.path("encoding").asText("");
            if ("base64".equals(encoding) && !content.isBlank())
                return new String(Base64.getDecoder().decode(content.replaceAll("\\s+", "")));
            return content;
        } catch (Exception e) {
            log.debug("Erro ao baixar {}: {}", path, e.getMessage());
            return null;
        }
    }

    private void auth(HttpHeaders h, String token) {
        if (token != null && !token.isBlank()) h.set("Authorization", "Bearer " + token);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Transactional
    protected void updateStatus(UUID id, GitHubRepo.IndexStatus status) {
        repoStore.findById(id).ifPresent(r -> {
            r.setIndexStatus(status); repoStore.save(r);
        });
    }

    @Transactional
    protected void updateStatusAndCount(UUID id, GitHubRepo.IndexStatus status,
                                         int count, String context) {
        repoStore.findById(id).ifPresent(r -> {
            r.setIndexStatus(status);
            r.setIndexedFilesCount(count);
            r.setLastIndexedAt(LocalDateTime.now());
            if (context != null) r.setContextIndex(context);
            repoStore.save(r);
        });
    }

    private boolean isIgnored(String path) {
        return IGNORED_DIRS.stream().anyMatch(d ->
                path.startsWith(d + "/") || path.equals(d));
    }

    private boolean isHighPriority(String path) {
        String l = path.toLowerCase();
        return l.equals("readme.md") || l.equals("pom.xml")
                || l.equals("package.json") || l.equals("go.mod");
    }

    private String ext(String path) {
        String l = path.toLowerCase();
        if (l.endsWith("dockerfile")) return "dockerfile";
        int i = path.lastIndexOf('.');
        if (i < 0 || i == path.length() - 1) return "";
        String e = path.substring(i + 1).toLowerCase();
        return e.contains("/") ? "" : e;
    }

    private record RepoFile(String path, String ext, long size) {}
}