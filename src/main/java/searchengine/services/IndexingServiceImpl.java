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
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

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
    //    private final LemmaRepository lemmaRepository;
    private List<String> indexingSites;
    private ForkJoinPool fjp;
    public static volatile boolean isStopped = true;

    @Override
    public IndexingResponse startIndexing() {
        IndexingResponse response = new IndexingResponse();
        indexingSites = new ArrayList<>();

        if (isStopped) {  // если индексация не запущена (остановлена)
            isStopped = false;  // снимаем флаг СТОП
            List<Site> sites = sitesList.getSites();  // получаем список сайтов для индексации

            for (Site site : sites) {  // в цикле получаем сайт и запускаем индексацию
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

    private Thread getThread(String siteUrl, String siteName) {
        SiteEntity siteEntity = indexingSite(siteName, siteUrl);
        siteRepository.save(siteEntity);

        ParseLinks parseLinks = new ParseLinks(siteEntity, siteUrl, new RefsList(), siteRepository, pageRepository);
        return new Thread(() -> {
            indexingSites.add(siteName);  // записываем в служебный список индексируемый сайт
            log.info(AnsiColor.ANSI_GREEN + "Запускаем индексацию сайта "
                    + AnsiColor.ANSI_PURPLE + siteName + AnsiColor.ANSI_RESET);

            fjp = new ForkJoinPool();
            long start = System.currentTimeMillis();
            fjp.invoke(parseLinks);

            String parserAnswer;
            try {
                parserAnswer = parseLinks.get();  // получаем ответ парсера
            } catch (InterruptedException | ExecutionException e) {
                log.info(AnsiColor.ANSI_RED + "Что-то пошло не так..." + AnsiColor.ANSI_RESET);
                throw new RuntimeException(e);
            }

            if (parserAnswer.matches("OK")) {
                log.info(AnsiColor.ANSI_GREEN + "Индексация сайта " + siteUrl
                        + " завершена" + AnsiColor.ANSI_RESET);
                indexingSites.remove(siteName);
                siteEntity.setStatus(IndexingStatus.INDEXED);
                indexedSite(siteEntity, start);
            } else if (parserAnswer.matches("Interrupted")) {
                indexingSites.clear();
                siteEntity.setStatus(IndexingStatus.FAILED);
                siteEntity.setLastError("Индексация остановлена пользователем");
                indexedSite(siteEntity, start);
                log.info(AnsiColor.ANSI_YELLOW + "Индексация остановлена пользователем" + AnsiColor.ANSI_RESET);
            } else if (parserAnswer.matches("UnknownHost")) {
                indexingSites.remove(siteName);
                siteEntity.setStatus(IndexingStatus.FAILED);
                siteEntity.setLastError("Указанный сайт не найден");
                indexedSite(siteEntity, start);
            }

            if (indexingSites.isEmpty() && !isStopped) {
                isStopped = true;
                log.info(AnsiColor.ANSI_CYAN + "Индексация завершена" + AnsiColor.ANSI_RESET);
            }
        });
    }


    private SiteEntity indexingSite(String name, String url) {
        SiteEntity site = new SiteEntity();
        site.setName(name);
        site.setUrl(url);
        site.setStatusTime(LocalDateTime.now());
        site.setStatus(IndexingStatus.INDEXING);
        return site;
    }

    private void indexedSite(SiteEntity site, long start) {
        int indexingTime = (int) ((System.currentTimeMillis() - start) / 1000);
        int pagesCount = pageRepository.pagesCount(site.getId());
        if (pagesCount > 0) {
            log.info(AnsiColor.ANSI_GREEN + "Сайт " + site.getUrl()
                    + ", проиндексировано " + pagesCount + " страниц "
                    + "за " + indexingTime + " сек" + AnsiColor.ANSI_RESET);
        }
        log.info("Индексируются сайты: " + AnsiColor.ANSI_PURPLE + indexingSites
                + AnsiColor.ANSI_RESET);

        site.setStatusTime(LocalDateTime.now());
        site.setIndexingTime(indexingTime);
        siteRepository.save(site);
    }
}
