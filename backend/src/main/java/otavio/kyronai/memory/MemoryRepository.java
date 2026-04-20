package otavio.kyronai.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    List<Memory> findByActiveTrueOrderByCreatedAtDesc();

    List<Memory> findAllByOrderByCreatedAtDesc();
}