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

import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

@Log4j2
@RequiredArgsConstructor
public class ParseLinks extends RecursiveTask<String> {
    private final SiteEntity siteEntity;
    private final String ref;
    private final RefsList refsList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final static String EXTRACT_REGEX = "(.+(\\.(jpg|pdf|doc|png|docx|xlsx|jpeg|mp4))$)";
    @Override
    protected String compute() {
        /*try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }*/

        try {
            Connection.Response response = Jsoup.connect(ref)
//                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36 Edg/111.0.1661.62")
                    .userAgent("YandexBot")
//                    .referrer("https://www.google.com")
                    .referrer("Search engines")
                    .ignoreHttpErrors(true)
                    .timeout(10000)
//                    .followRedirects(true)
                    .execute();

            int statusCode = response.statusCode();
            if (statusCode == 200) {
                Document doc = response.parse();
                savePage(statusCode, ref, doc.html(), siteEntity);
//            saveStatusTime(); // зачем это нужно? сильно замедляет работу


//                System.out.println("Title - " + doc.title());
                System.out.println("---------------   " + ref);
                System.out.println("Исходный текст: " + doc.text());
                Lemmatizer lemmatizer = new Lemmatizer();
                Map<String, Integer> lemmas = lemmatizer.getLemmas(doc.text());  // подумать что с этим делать

//                lemmatizer.saveLemma(lemmas);

                Elements elements = doc.select("body").select("a");
                fetchLinks(elements);
            } else {
                savePage(statusCode, ref, response.statusMessage(), siteEntity);
            }
        } catch (UnknownHostException e) {
            return "UnknownHost";
        } catch (CancellationException cex) {
            return "Interrupted";
        } catch (Exception ex) {
            httpHandler(ex);
        }

        if (IndexingServiceImpl.isStopped) { // если нажата СТОП, но не весь сайт проиндексирован
            return "Interrupted";
        }

        return "OK";
    }

    private void fetchLinks(Elements elements) {
        List<ParseLinks> taskList = new ArrayList<>();
        for (Element link : elements) {
            String fetchedLink = URLDecoder.decode(link.absUrl("href"), StandardCharsets.UTF_8);
            if (isCorrectLink(fetchedLink, siteEntity.getUrl()) &&
                    (!refsList.isRefPresent(fetchedLink) && !IndexingServiceImpl.isStopped)) {
                refsList.addRef(fetchedLink);

                ParseLinks task = new ParseLinks(siteEntity, fetchedLink, refsList, siteRepository, pageRepository);
                task.fork();
                taskList.add(task);
            }
        }
        taskList.forEach(ForkJoinTask::join);
    }

    private void savePage(int statusCode, String ref, String content, SiteEntity siteEntity) {
        PageEntity page = new PageEntity();
        page.setCode(statusCode);
        page.setPath(getRelativePath(ref));
        page.setContent(content);
        page.setSiteId(siteEntity);
        pageRepository.save(page);

        if (statusCode != 200) {
            log.info(AnsiColor.ANSI_PURPLE + "HTTP code: " + AnsiColor.ANSI_RED
                    + statusCode + ", " + content + ", " + AnsiColor.ANSI_RESET + ref);
        }
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
                && !fetchedLink.matches(EXTRACT_REGEX)
                && !fetchedLink.contains(".html/");
    }

    private void httpHandler(Exception ex) {
        String msg = ex.getMessage();
        if (msg.contains("Unhandled content type")) {
            savePage(405, ref, msg, siteEntity);
//        } else if (msg.contains("Status=500")) {
//            savePage(500, ref, msg, siteEntity);
        } else {
            savePage(888, ref, msg, siteEntity);
        }
    }
}
