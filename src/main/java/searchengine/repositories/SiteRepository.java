package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteEntity;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {
    @Query(value = "SELECT COUNT(*) FROM site", nativeQuery = true)
    int sitesCount();

    @Query(value = "SELECT * FROM site WHERE url = :url", nativeQuery = true)
    SiteEntity site(String url);

//    @Query(value = "SELECT id FROM site WHERE main_url = :url", nativeQuery = true)
//    int siteId(String url);

//    @Query(value = "SELECT status_time FROM site WHERE id = :id", nativeQuery = true)
//    LocalDateTime statusTime(int id);

//    @Query(value = "SELECT last_error FROM site WHERE id = :id", nativeQuery = true)
//    String lastError(int id);
}
