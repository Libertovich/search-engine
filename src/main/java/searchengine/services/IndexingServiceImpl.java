package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.AnsiColor;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.RefsList;
import searchengine.model.*;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Service
@Log4j2
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sitesList;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    private ForkJoinPool fjp;
    private final List<String> indexingSitesCount = new ArrayList<>();
//    private SiteEntity siteEntity;

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
                    log.info(AnsiColor.ANSI_GREEN + "Индексируются сайты: " + AnsiColor.ANSI_PURPLE
                            + indexingSitesCount + AnsiColor.ANSI_RESET);
                    fjp = new ForkJoinPool();
                    fjp.invoke(parseLinks);

                    int indexingTime = (int) ((System.currentTimeMillis() - start) / 1000);
                    indexedSite(siteEntity, indexingTime);
//                    siteEntity.setStatus(IndexingStatus.INDEXED);
//                    siteEntity.setStatusTime(LocalDateTime.now());
//
//                    siteEntity.setIndexingTime(idxTime);
//                    siteRepository.save(siteEntity);

                    indexingSitesCount.remove(siteName);
                    log.info(AnsiColor.ANSI_BLUE + "Индексируются сайты: " + AnsiColor.ANSI_PURPLE
                            + indexingSitesCount + AnsiColor.ANSI_RESET);
                    System.out.printf("Сайт %s, проиндексировано %d страниц за %d сек\n", siteUrl,
                            pageRepository.pagesCount(siteEntity.getId()), indexingTime); // строку можно оставить?
                });
                thread.start();
            }
            response.setResult(true);
        } else {
            response.setResult(false);
            response.setError("Индексация уже запущена");
        }
/*
        response.setResult(false);
        response.setError("Указанная страница не найдена");
*/
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        IndexingResponse response = new IndexingResponse();
        if (!indexingSitesCount.isEmpty()) {
            fjp.shutdownNow();
            response.setResult(true);
            log.info(AnsiColor.ANSI_BLUE + "Индексация остановлена пользователем" + AnsiColor.ANSI_RESET);
/*            SiteEntity siteEntity = new SiteEntity();
            indexingSitesCount.forEach(s -> {
                siteEntity.setStatus(IndexingStatus.FAILED);
                siteEntity.setLastError("Индексация остановлена пользователем");
                siteRepository.save(siteEntity);
            });*/
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

    private void indexedSite(SiteEntity site, int indexingTime) {
        site.setStatus(IndexingStatus.INDEXED);
        site.setStatusTime(LocalDateTime.now());
        site.setIndexingTime(indexingTime);
        siteRepository.save(site);
    }
}
