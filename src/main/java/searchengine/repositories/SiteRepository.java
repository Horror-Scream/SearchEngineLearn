package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.models.SiteEntity;

import java.util.Optional;

public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {
    Optional<SiteEntity> findByUrl(String url);

}