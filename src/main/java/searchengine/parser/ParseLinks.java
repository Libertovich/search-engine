package searchengine.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.AnsiColor;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingServiceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

@Log4j2
@RequiredArgsConstructor
//@ConfigurationProperties(prefix = "jsoup-settings") // настроить подключение
public class ParseLinks extends RecursiveTask<Boolean> {
    private final SiteEntity siteEntity;
    private final String ref;
    private final RefsList refsList; // делал final нестатик, передавал из fjp как параметр. Время -3% и еще один параметр для передачи
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    @Override
    protected Boolean compute() {
        List<ParseLinks> taskList = new ArrayList<>();

//        try {
//            Thread.sleep(5000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }

        try {
            Connection.Response response = Jsoup.connect(ref)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36 Edg/111.0.1661.62")
                    .referrer("https://www.google.com")
                    .ignoreHttpErrors(true)
//                    .timeout(5000)
                    .followRedirects(true) // убирает коды НТТР 301 и 302
                    .execute();

            Document doc = response.parse();
            savePage(response.statusCode(), ref, doc.html(), siteEntity);
//            saveStatusTime(); // зачем это нужно? сильно замедляет работу
            Elements elements = doc.select("body").select("a");
            for (Element link : elements) {
                String fetchedLink = link.absUrl("href");
                if (isCorrectLink(fetchedLink, siteEntity.getUrl())) {
                    if (!refsList.isRefPresent(fetchedLink) && !IndexingServiceImpl.isStopped) {
                        refsList.addRef(fetchedLink);

                        ParseLinks task = new ParseLinks(siteEntity, fetchedLink, refsList, siteRepository, pageRepository);
                        task.fork();
                        taskList.add(task);
                    }
                }
            }

            taskList.forEach(ForkJoinTask::join);
        } catch (Exception ex) {
            if (ex.getMessage() == null) {
                return false;
            }
            httpCodeCheck(ex);
        }
        return true;
    }

    private void savePage(int statusCode, String ref, String html, SiteEntity siteEntity) {
        PageEntity page = new PageEntity();
        page.setCode(statusCode);
        page.setPath(getRelativePath(ref));
        page.setContent(html);
        page.setSiteId(siteEntity);
        pageRepository.save(page);
    }

    private void saveStatusTime() {
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }

    private String getRelativePath(String ref) {
        String relativePath = ref.substring(siteEntity.getUrl().length());
        return !relativePath.isEmpty() ? relativePath : "/";
    }

    private boolean isCorrectLink(String fetchedLink, String siteUrl) {
        fetchedLink = fetchedLink.toLowerCase();
        return fetchedLink.startsWith(siteUrl)
                && !fetchedLink.equals(siteUrl)
                && !fetchedLink.equals(siteUrl.concat("/"))
                && fetchedLink.matches("[^#?]+")
                && !fetchedLink.matches("(.+(\\.(jpg|pdf|doc|png|docx|xlsx|jpeg))$)")
                && !fetchedLink.contains(".html/");
    }

    private void httpCodeCheck(Exception ex) {
        String msg = ex.getMessage();
        if (msg.contains("Status=404")) {
            savePage(404, ref, msg, siteEntity);
            log.info(AnsiColor.ANSI_YELLOW + "Reference info: " + AnsiColor.ANSI_PURPLE
                    + "HTTP 404, Not found, " + AnsiColor.ANSI_RESET + ref);
        } else if (msg.contains("Unhandled content type")) {
            savePage(405, ref, msg, siteEntity);
            log.info(AnsiColor.ANSI_GREEN + "Reference info: " + AnsiColor.ANSI_PURPLE
                    + "HTTP 405, Unhandled content type, " + AnsiColor.ANSI_RESET + ref);
        } else if (msg.contains("Status=500")) {
            savePage(500, ref, msg, siteEntity);
            log.error(AnsiColor.ANSI_RED + "Reference info: " + AnsiColor.ANSI_YELLOW
                    + "HTTP 500, Internal server error, " + AnsiColor.ANSI_RESET + ref);
        } else {
            savePage(888, ref, msg, siteEntity);
            log.info(AnsiColor.ANSI_YELLOW + "Info: " + msg + AnsiColor.ANSI_RESET + ref);
        }
    }
}
