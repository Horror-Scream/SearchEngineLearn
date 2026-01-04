package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.models.Lemma;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    @Query("SELECT l FROM Lemma l WHERE l.site.id = :siteId AND l.lemma = :lemma")
    Optional<Lemma> findBySiteAndLemma(@Param("siteId") int siteId, @Param("lemma") String lemma);

    List<Lemma> findBySiteId(int siteId);

    @Modifying
    @Query("DELETE FROM Lemma l WHERE l.site.id = :siteId")
    void deleteBySiteId(@Param("siteId") int siteId);

    @Query("SELECT l FROM Lemma l WHERE l.lemma IN :lemmas")
    List<Lemma> findByLemmas(@Param("lemmas") List<String> lemmas);

    @Query("SELECT l FROM Lemma l WHERE l.lemma IN :lemmas AND l.site.id = :siteId")
    List<Lemma> findByLemmasAndSite(@Param("lemmas") List<String> lemmas, @Param("siteId") int siteId);

    @Query("SELECT i.page.id FROM SearchIndex i WHERE i.lemma.id = :lemmaId")
    Set<Integer> findPageIdsByLemmaId(@Param("lemmaId") int lemmaId);
}