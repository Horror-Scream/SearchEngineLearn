package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.models.SearchIndex;

import java.util.List;

public interface IndexRepository extends JpaRepository<SearchIndex, Integer> {
    @Modifying
    @Query("DELETE FROM SearchIndex i WHERE i.page.id = :pageId")
    void deleteByPageId(@Param("pageId") int pageId);

    @Query("SELECT i FROM SearchIndex i WHERE i.page.id = :pageId")
    List<SearchIndex> findByPageId(@Param("pageId") int pageId);
}