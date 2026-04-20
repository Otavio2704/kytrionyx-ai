package otavio.kyronai.github;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GitHubRepoStore extends JpaRepository<GitHubRepo, UUID> {

    List<GitHubRepo> findAllByOrderByCreatedAtDesc();

    Optional<GitHubRepo> findByFullName(String fullName);
}