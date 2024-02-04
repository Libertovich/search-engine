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
import searchengine.parser.LinksParser;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.repositories.LemmaRepository;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
@Log4j2
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private List<String> indexingSites;
    private ForkJoinPool fjp;
    public static volatile boolean isStopped = true;

    @Override
    public IndexingResponse startIndexing() {
        IndexingResponse response = new IndexingResponse();
        indexingSites = new ArrayList<>();

        if (isStopped) {
            isStopped = false;
//            log.info("Начало очистки базы");
            siteRepository.clearSiteTable();
//            log.info("Окончание очистки базы");

            List<Site> sites = sitesList.getSites();
            for (Site site : sites) {
                Thread thread = getThread(site.getUrl(), site.getName());
                thread.start();
            }

            response.setResult(true);
        } else {
            response.setResult(false);
            response.setError("Индексация уже запущена");
        }
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        IndexingResponse response = new IndexingResponse();
        log.info(AnsiColor.ANSI_YELLOW + "Нажата кнопка STOP" + AnsiColor.ANSI_RESET);

        if (!isStopped) {
            isStopped = true;
            fjp.shutdown(); // Disable new tasks from being submitted
            fjpAwaitTermination();

            response.setResult(true);
        } else {
            response.setResult(false);
            response.setError("Индексация не запущена");
        }
        return response;
    }

    @Override
    public IndexingResponse indexPage(String page) {
        IndexingResponse response = new IndexingResponse();

        String indexPage = URLDecoder.decode( page, StandardCharsets.UTF_8).substring(4);
        List<Site> sites = sitesList.getSites();
        int pageCount = 0;
        for (Site site : sites) {
            if (indexPage.startsWith(site.getUrl())) {
                pageCount++;
            }
        }

        if (pageCount > 0) {
            System.out.println("Страница для индексации/переиндексации - " + indexPage);

// что-то сделать дальше (индексировать?)


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

    private Thread getThread(String siteUrl, String siteName) {
        SiteEntity siteEntity = indexingSite(siteName, siteUrl);
        siteRepository.save(siteEntity);

        LinksParser linksParser = new LinksParser(siteEntity, siteUrl, new RefsList(), siteRepository, pageRepository, lemmaRepository);
        return new Thread(() -> {
            indexingSites.add(siteName);  // записываем в служебный список индексируемый сайт
            log.info(AnsiColor.ANSI_GREEN + "Запускаем индексацию сайта "
                    + AnsiColor.ANSI_PURPLE + siteName + AnsiColor.ANSI_RESET);

            fjp = new ForkJoinPool();
            long start = System.currentTimeMillis();
            fjp.invoke(linksParser);

            String parserStatus;
            try {
                parserStatus = linksParser.get();  // получаем ответ парсера
            } catch (InterruptedException | ExecutionException e) {
                log.info(AnsiColor.ANSI_RED + "С парсингом что-то пошло не так..." + AnsiColor.ANSI_RESET);
                throw new RuntimeException(e);
            }

            saveIndexedSite(siteEntity, parserStatus, start);

            if (indexingSites.isEmpty() && !isStopped) {
                isStopped = true;
                log.info(AnsiColor.ANSI_CYAN + "Индексация завершена" + AnsiColor.ANSI_RESET);
            }
        });
    }

    private void fjpAwaitTermination() {
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
    }

    private SiteEntity indexingSite(String name, String url) {
        SiteEntity site = new SiteEntity();
        site.setName(name);
        site.setUrl(url);
        site.setStatusTime(LocalDateTime.now());
        site.setStatus(IndexingStatus.INDEXING);
        return site;
    }

    private void saveIndexedSite(SiteEntity site, String parserStatus, long start) {
        String siteUrl = site.getUrl();
        String siteName = site.getName();
        if (parserStatus.matches("OK")) {
            log.info(AnsiColor.ANSI_GREEN + "Индексация сайта " + siteUrl
                    + " завершена" + AnsiColor.ANSI_RESET);
            indexingSites.remove(siteName);
            indexedSite(site, start, "INDEXED", "");
        } else if (parserStatus.matches("Interrupted")) {
            indexingSites.clear();
            indexedSite(site, start, "FAILED", "Индексация остановлена пользователем");
            log.info(AnsiColor.ANSI_YELLOW + "Индексация сайта " + siteUrl
                    + " остановлена пользователем" + AnsiColor.ANSI_RESET);
        } else if (parserStatus.matches("UnknownHost")) {
            indexingSites.remove(siteName);
            indexedSite(site, start, "FAILED", "Указанный сайт не найден");
            log.info(AnsiColor.ANSI_YELLOW + "Сайт " + siteUrl + " не найден!" + AnsiColor.ANSI_RESET);
        }
    }

    private void indexedSite(SiteEntity site, long start, String status, String error) {
        int indexingTime = (int) ((System.currentTimeMillis() - start) / 1000);
        site.setStatusTime(LocalDateTime.now());
        site.setIndexingTime(indexingTime);
        if (status.matches("INDEXED")) {
            site.setStatus(IndexingStatus.INDEXED);
        } else {
            site.setStatus(IndexingStatus.FAILED);
        }
        site.setLastError(error);
        siteRepository.save(site);

        int pagesCount = pageRepository.pagesCount(site.getId());
        if (pagesCount > 0) {
            log.info(AnsiColor.ANSI_GREEN + "Сайт " + site.getUrl() + ", проиндексировано "
                    + pagesCount + " страниц " + "за " + indexingTime + " сек" + AnsiColor.ANSI_RESET);
        }
        log.info("Индексируются сайты: " + AnsiColor.ANSI_PURPLE + indexingSites + AnsiColor.ANSI_RESET);
    }
}
