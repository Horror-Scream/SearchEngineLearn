package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.models.Page;
import searchengine.models.SiteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {

    @Query("SELECT p FROM Page p WHERE p.site.id = :siteId AND p.path = :path")
    Optional<Page> findBySiteIdAndPath(@Param("siteId") int siteId, @Param("path") String path);

    int countBySiteId(int siteId);

    @Query("SELECT p FROM Page p WHERE p.site.id = :siteId")
    List<Page> findBySiteId(@Param("siteId") int siteId);

    void deleteAllBySite(SiteEntity site);

    @Query("SELECT p FROM Page p WHERE p.id IN :pageIds")
    List<Page> findByIdIn(@Param("pageIds") List<Integer> pageIds);

}