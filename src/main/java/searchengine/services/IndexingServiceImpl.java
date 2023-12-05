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
    private final List<String> indexingSitesCount = new ArrayList<>();  // перенести в метод startIndexing?
    private ForkJoinPool fjp;
    public static volatile boolean isStopped = true;

    @Override
    public IndexingResponse startIndexing() {
        IndexingResponse response = new IndexingResponse();

        if (isStopped) {  // если индексация не запущена (остановлена)
                isStopped = false;  // снимаем флаг СТОП
                List<Site> sites = sitesList.getSites();  // получаем список сайтов для индексации
                for (Site site : sites) {  // в цикле получаем сайт и запускаем индексацию
                    String siteName = site.getName();  // получаем имя сайта
                    String siteUrl = site.getUrl();  // получаем адрес сайта

                    Thread thread = getThread(siteUrl, siteName);  // создаем поток
                    thread.start();  // и запускаем его
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
//        if (!indexingSitesCount.isEmpty()) {
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
                isStopped = true;
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
        SiteEntity siteEntity = indexingSite(siteName, siteUrl);  // создаем сайт-сущность
        siteRepository.save(siteEntity);  // и записываем его в базу

        ParseLinks parseLinks = new ParseLinks(siteEntity, siteUrl, new RefsList(), siteRepository, pageRepository);
        return new Thread(() -> {  // состав потока
            indexingSitesCount.add(siteName);  // записываем в служебный список индексируемый сайт - для чего???
            log.info(AnsiColor.ANSI_YELLOW + "Запускаем индексацию сайта "
                    + AnsiColor.ANSI_PURPLE + siteName + AnsiColor.ANSI_RESET);

            fjp = new ForkJoinPool();
            long start = System.currentTimeMillis();  // запоминаем время начала индексации
            if (fjp.invoke(parseLinks)) {  // если индексация сайта завершилась нормально
                int indexedTime = indexedTime(start);  // получаем время окончания индексации
                indexedSite(siteEntity, indexedTime);  // и записываем в базу данные об индексации
                indexingSitesCount.remove(siteName);  // удаляем из служебного списка проиндексированный сайт - для чего???

                if (indexingSitesCount.isEmpty()) isStopped = true;  // ставим флаг в СТОП

                log.info("Сайт " + siteUrl + ", проиндексировано "
                        + AnsiColor.ANSI_GREEN + pageRepository.pagesCount(siteEntity.getId()) + " страниц "
                        + "за " + indexedTime + " сек" + AnsiColor.ANSI_RESET);
                log.info("Индексируются сайты: " + AnsiColor.ANSI_PURPLE + indexingSitesCount
                        + AnsiColor.ANSI_RESET);
            } else {  // если индексация сайта завершилась НЕнормально (остановлена пользователем)
                indexedSite(siteEntity, indexedTime(start));  // записываем в базу данные об индексации
                indexingSitesCount.clear();  // очищаем служебный список индексируемых сайтов
                log.info(AnsiColor.ANSI_BLUE + "Индексация остановлена пользователем" + AnsiColor.ANSI_RESET);
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

    private Integer indexedTime(long start) {
        return (int) ((System.currentTimeMillis() - start) / 1000);
    }

    private void indexedSite(SiteEntity site, int indexedTime) {
        if (isStopped) {
            site.setStatus(IndexingStatus.FAILED);
            site.setLastError("Индексация остановлена пользователем");
        } else {
            site.setStatus(IndexingStatus.INDEXED);
        }
        site.setStatusTime(LocalDateTime.now());
        site.setIndexingTime(indexedTime);
        siteRepository.save(site);
    }
}
