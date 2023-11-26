package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import searchengine.config.AnsiColor;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.parser.RefsList;
import searchengine.model.*;
import searchengine.parser.ParseLinks;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
@Log4j2
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    //    private final LemmaRepository lemmaRepository;
    private final List<String> indexingSitesCount = new ArrayList<>();
    private ForkJoinPool fjp;

    @Override
    public IndexingResponse startIndexing() {
        long start = System.currentTimeMillis();
        IndexingResponse response = new IndexingResponse();

        if (indexingSitesCount.isEmpty()) {
            List<Site> sites = sitesList.getSites();
            for (Site site : sites) {
                String siteName = site.getName();
                String siteUrl = site.getUrl();
                SiteEntity siteEntity = indexingSite(siteName, siteUrl);
                siteRepository.save(siteEntity);

                ParseLinks parseLinks = new ParseLinks(siteEntity, siteUrl, new RefsList(), siteRepository, pageRepository);
                Thread thread = new Thread(() -> {
                    indexingSitesCount.add(siteName);
                    log.info(AnsiColor.ANSI_YELLOW + "Запускаем индексацию сайта "
                            + AnsiColor.ANSI_PURPLE + siteName + AnsiColor.ANSI_RESET);

                    fjp = new ForkJoinPool();
                    if (fjp.invoke(parseLinks)) {
                        int indexedTime = (int) ((System.currentTimeMillis() - start) / 1000);
                        indexedSite(siteEntity, indexedTime);
                        indexingSitesCount.remove(siteName);
                        log.info("Сайт " + siteUrl + ", проиндексировано "
                                + AnsiColor.ANSI_GREEN + pageRepository.pagesCount(siteEntity.getId()) + " страниц "
                                + "за " + indexedTime + " сек" + AnsiColor.ANSI_RESET);
                        log.info("Индексируются сайты: " + AnsiColor.ANSI_PURPLE + indexingSitesCount
                                + AnsiColor.ANSI_RESET);
                    } else {
                        indexedFailed(siteEntity);
                        indexingSitesCount.clear();
                        log.info(AnsiColor.ANSI_BLUE + "Индексация остановлена пользователем" + AnsiColor.ANSI_RESET);
                    }
                });
                thread.start();
            }
            response.setResult(true);
        } else {
            response.setResult(false);
            response.setError("Индексация уже запущена");
        }
/* // TODO реализовать
        response.setResult(false);
        response.setError("Указанная страница не найдена");
*/
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        IndexingResponse response = new IndexingResponse();

        log.info(AnsiColor.ANSI_YELLOW + "Нажата кнопка STOP" + AnsiColor.ANSI_RESET);
        if (!indexingSitesCount.isEmpty()) {
            fjp.shutdown(); // Disable new tasks from being submitted
            try {
                if (!fjp.awaitTermination(10, TimeUnit.SECONDS)) {
                    fjp.shutdownNow(); // Cancel currently executing tasks
                    if (!fjp.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.error(AnsiColor.ANSI_RED + "Pool did not terminate" + AnsiColor.ANSI_RESET);
                    }
                }
            } catch (InterruptedException ie) {
                fjp.shutdownNow(); // (Re-)Cancel if current thread also interrupted
                Thread.currentThread().interrupt(); // Preserve interrupt status
            }
            response.setResult(true);
        } else {
            response.setResult(false);
            response.setError("Индексация не запущена");
        }
        return response;
    }

    @Override
    public IndexingResponse indexPage() {
        IndexingResponse response = new IndexingResponse();

        if (true) { // написать условие
            response.setResult(true);
        } else {
            response.setResult(false);
            response.setError("Данная страница не принадлежит сайтам, указанным в конфигурационном файле");
        }
        return response;
    }

    @Override
    public IndexingResponse search() {
        IndexingResponse response = new IndexingResponse();

        if (true) { // написать условие
            response.setResult(true);
        } else {
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
        }
        return response;
    }

    private SiteEntity indexingSite(String name, String url) {
        SiteEntity site = new SiteEntity();
        site.setName(name);
        site.setUrl(url);
        site.setStatusTime(LocalDateTime.now());
        site.setStatus(IndexingStatus.INDEXING);
        return site;
    }

    private void indexedSite(SiteEntity site, int indexedTime) {
        site.setStatus(IndexingStatus.INDEXED);
        site.setStatusTime(LocalDateTime.now());
        site.setIndexingTime(indexedTime);
        siteRepository.save(site);
    }

    private void indexedFailed(SiteEntity site) {
        site.setStatus(IndexingStatus.FAILED);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError("Индексация остановлена пользователем");
        siteRepository.save(site);
    }
}
