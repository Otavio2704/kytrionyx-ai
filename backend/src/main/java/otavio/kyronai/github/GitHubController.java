package otavio.kyronai.github;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GitHubController {

    private final GitHubService gitHubService;

    @GetMapping("/repositories")
    public ResponseEntity<List<GitHubRepoDTO>> list() {
        return ResponseEntity.ok(gitHubService.getAllRepos());
    }

    @PostMapping("/repositories")
    public ResponseEntity<Object> connect(@RequestBody Map<String, Object> body) {
        String  fullName    = (String)  body.get("fullName");
        String  repoUrl     = (String)  body.get("repoUrl");
        String  branch      = (String)  body.getOrDefault("branch", "main");
        String  accessToken = (String)  body.get("accessToken");
        boolean isPrivate   = Boolean.TRUE.equals(body.get("isPrivate"));

        String repositoryInput = repoUrl != null && !repoUrl.isBlank() ? repoUrl : fullName;
        if (repositoryInput == null || repositoryInput.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "URL ou owner/repo é obrigatório"));

        try {
            return ResponseEntity.ok(gitHubService.connectRepo(repositoryInput, branch, accessToken, isPrivate));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/repositories/{id}/reindex")
    public ResponseEntity<GitHubRepoDTO> reindex(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(gitHubService.reindex(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/repositories/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return gitHubService.deleteRepo(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/repositories/{id}/context")
    public ResponseEntity<Map<String, String>> context(@PathVariable UUID id) {
        String ctx = gitHubService.buildContext(id);
        return ctx != null
                ? ResponseEntity.ok(Map.of("context", ctx))
                : ResponseEntity.notFound().build();
    }
}