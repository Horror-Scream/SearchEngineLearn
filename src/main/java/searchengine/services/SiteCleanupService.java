package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.Page;
import searchengine.models.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;

import java.util.List;

@Slf4j
@Service
public class SiteCleanupService {

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Transactional
    public void resetSiteData(SiteEntity site) {
        log.info("🧹 Очистка данных для сайта: {}", site.getName());

        try {
            List<Page> pages = pageRepository.findBySiteId(site.getId());
            for (Page page : pages) {
                indexRepository.deleteByPageId(page.getId());
            }

            pageRepository.deleteAllBySite(site);
            lemmaRepository.deleteBySiteId(site.getId());

            log.info("🗑️ Данные сайта {} успешно очищены", site.getName());
        } catch (Exception e) {
            log.error("💥 Ошибка при очистке данных для сайта {}", site.getName(), e);
            throw new RuntimeException("Ошибка очистки данных сайта: " + site.getName(), e);
        }
    }
}