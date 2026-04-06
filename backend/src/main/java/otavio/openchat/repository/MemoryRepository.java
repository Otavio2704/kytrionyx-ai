package otavio.openchat.repository;

import otavio.openchat.model.Memory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    /** Retorna todas as memórias ativas, ordenadas por data de criação */
    List<Memory> findByActiveTrueOrderByCreatedAtDesc();

    /** Retorna todas as memórias (ativas e inativas) para gerenciamento */
    List<Memory> findAllByOrderByCreatedAtDesc();

    /** Busca por categoria */
    List<Memory> findByCategoryAndActiveTrueOrderByCreatedAtDesc(String category);
}