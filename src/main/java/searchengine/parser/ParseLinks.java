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

import java.net.UnknownHostException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

@Log4j2
@RequiredArgsConstructor
//@ConfigurationProperties(prefix = "jsoup-settings") // настроить подключение
public class ParseLinks extends RecursiveTask<String> {
    private final SiteEntity siteEntity;
    private final String ref;
    private final RefsList refsList; // делал final нестатик, передавал из fjp как параметр. Время -3% и еще один параметр для передачи
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    @Override
    protected String compute() {
        List<ParseLinks> taskList = new ArrayList<>();
        String url = siteEntity.getUrl();

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

            int statusCode = response.statusCode();
            if (statusCode == 200) {
                Document doc = response.parse();
                savePage(statusCode, ref, doc.html(), siteEntity);
//            saveStatusTime(); // зачем это нужно? сильно замедляет работу
                Elements elements = doc.select("body").select("a");
                for (Element link : elements) {
                    String fetchedLink = link.absUrl("href");
                    if (isCorrectLink(fetchedLink, url)) {
                        if (!refsList.isRefPresent(fetchedLink) && !IndexingServiceImpl.isStopped) {
                            refsList.addRef(fetchedLink);

                            ParseLinks task = new ParseLinks(siteEntity, fetchedLink, refsList, siteRepository, pageRepository);
                            task.fork();
                            taskList.add(task);
                        }
                    }
                }
                taskList.forEach(ForkJoinTask::join);
            } else {
                savePage(statusCode, ref, "Not found", siteEntity);
                log.info(AnsiColor.ANSI_RED + "Exc: " + AnsiColor.ANSI_PURPLE
                        + "HTTP 404, Not found, " + AnsiColor.ANSI_RESET + ref);
            }
        } catch (UnknownHostException e) {
            log.info(AnsiColor.ANSI_RED + "Exc: " + AnsiColor.ANSI_PURPLE
                    + "Сайт " + url + " не найден!" + AnsiColor.ANSI_RESET);
            return "UnknownHost";
        } catch (CancellationException cex) {
            log.info(AnsiColor.ANSI_RED + "Exc: " + AnsiColor.ANSI_PURPLE
                    + "Индексация " + url + " прервана пользователем" + AnsiColor.ANSI_RESET);
            return "Interrupted";
        } catch (Exception ex) {
            httpCodeCheck(ex);
        }

        if (IndexingServiceImpl.isStopped) {  // если нажата СТОП, но не весь сайт проиндексирован
            return "Interrupted";
        }

        return "OK";
    }

    private void savePage(int statusCode, String ref, String html, SiteEntity siteEntity) {
        PageEntity page = new PageEntity();
        page.setCode(statusCode);
        page.setPath(getRelativePath(ref));
        page.setContent(html);
        page.setSiteId(siteEntity);
        pageRepository.save(page);
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
        if (msg.contains("Unhandled content type")) {
            savePage(405, ref, msg, siteEntity);
            log.info(AnsiColor.ANSI_RED + "Exc: " + AnsiColor.ANSI_PURPLE
                    + "HTTP 405, Unhandled content type, " + AnsiColor.ANSI_RESET + ref);
        } else if (msg.contains("Status=500")) {
            savePage(500, ref, msg, siteEntity);
            log.error(AnsiColor.ANSI_RED + "Exc: "
                    + "HTTP 500, Internal server error, " + AnsiColor.ANSI_RESET + ref);
        } else {
            savePage(888, ref, msg, siteEntity);
            log.info(AnsiColor.ANSI_RED + "Exc: " + msg + AnsiColor.ANSI_RESET + " " + ref);
        }
    }
}
