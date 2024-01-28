package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = "DELETE FROM `site`", nativeQuery = true)
    void clearSiteTable();

    @Query(value = "SELECT COUNT(*) FROM site", nativeQuery = true)
    int sitesCount();

    @Query(value = "SELECT * FROM site WHERE url = :url", nativeQuery = true)
    SiteEntity site(String url);


//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Transactional
//    @Query(value = "DELETE FROM `index`", nativeQuery = true)
//    void clearIndexTable();


//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Transactional
//    @Query(value = "DELETE FROM `lemma`", nativeQuery = true)
//    void clearLemmaTable();


//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Transactional
//    @Query(value = "DELETE FROM `page`", nativeQuery = true)
//    void clearPageTable();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM `site` WHERE `name` = :name", nativeQuery = true)
    void deleteByName(String name);

//    @Modifying
//    @Transactional
//    @Query(value = "DELETE FROM lemma WHERE lemma.site_id = :id", nativeQuery = true)
//    void deleteAllBySiteId(@Param("id") long id);

//    @Query(value = "SELECT * FROM site WHERE status = 'INDEXING'", nativeQuery = true)
//    ArrayList<SiteEntity> notIndexedSite();

//    @Query(value = "SELECT id FROM site WHERE main_url = :url", nativeQuery = true)
//    int siteId(String url);

//    @Query(value = "SELECT status_time FROM site WHERE id = :id", nativeQuery = true)
//    LocalDateTime statusTime(int id);

//    @Query(value = "SELECT last_error FROM site WHERE id = :id", nativeQuery = true)
//    String lastError(int id);
}
