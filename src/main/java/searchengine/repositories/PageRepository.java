package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    @Query(value = "SELECT COUNT(*) FROM page WHERE site_id = :siteId AND code = 200", nativeQuery = true)
    int pagesCount(int siteId);

    @Query(value = "SELECT COUNT(*) FROM page WHERE site_id = :siteId AND code = 404", nativeQuery = true)
    int httpNotFound(int siteId);

//    @Query(value = "SELECT path FROM page WHERE path = :path AND site_id = :siteId", nativeQuery = true)
//    Optional<String> findByPathAndSite(String path, int siteId);
}
