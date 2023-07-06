package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    @Query(value = "SELECT COUNT(*) FROM lemma WHERE site_id = :siteId", nativeQuery = true)
    int lemmaCount(int siteId);
}
