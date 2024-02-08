package searchengine.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.AnsiColor;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingServiceImpl;

import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

@Log4j2
@RequiredArgsConstructor
public class LinksParser extends RecursiveTask<String> {
    private final SiteEntity siteEntity;
    private final String ref;
    private final RefsList refsList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    private final static String EXTRACT_REGEX = "(.+(\\.(jpg|pdf|doc|png|docx|xlsx|jpeg|mp4))$)";
    private final static Set<LemmaEntity> lemmaEntitySet = Collections.synchronizedSet(new HashSet<>());

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
//                System.out.println(ref + " Исходный текст: " + doc.body().text());
                Lemmatizer lemmatizer = new Lemmatizer();
                Set<String> lemmas = lemmatizer.getLemmas(doc.body().text());
//                System.out.println(ref + " - " + lemmas.size() + " лемм"/*+ lemmas*/);
                if (lemmas != null) {
                    getLemmaEntitySet(lemmas);
                }

                System.out.println(siteEntity.getUrl() + " - " + "LemmaEntitySet - " + lemmaEntitySet.size());

         /*       List<String> lemmaList = new ArrayList<>();
                for (LemmaEntity lemma : lemmaEntitySet) {
                    lemmaList.add(lemma.getLemma());
                }
                System.out.println(lemmaList);*/

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
            handleException(ex);
        }

        if (IndexingServiceImpl.isStopped) { // если нажата СТОП, но не весь сайт проиндексирован
            return "Interrupted";
        }

        synchronized (lemmaEntitySet) {
            lemmaRepository.saveAll(lemmaEntitySet);
        }
        return "OK";
    }

    private void getLemmaEntitySet(Set<String> lemmas) {
        for (String lemma : lemmas) {
            LemmaEntity lemmaEntity = new LemmaEntity();
//                    synchronized (lemmaEntitySet) {
            if (!lemmaEntitySet.isEmpty()) {
                int i = 0;
                synchronized (lemmaEntitySet) {
                    for (LemmaEntity lemmaEnt : lemmaEntitySet) {
                        if (lemmaEnt.getLemma().matches(lemma) && lemmaEnt.getSiteId().equals(siteEntity)) {  // добавить совпадение по siteEntity?
                            lemmaEnt.setFrequency(lemmaEnt.getFrequency() + 1);
                            i++;
                            break;
                        }
                    }
                }
                if (i == 0) {
                    addLemmaEntity(lemmaEntity, lemma);
                }
            } else {
                addLemmaEntity(lemmaEntity, lemma);
            }
//                    }
        }
    }

    private void addLemmaEntity(LemmaEntity lemmaEntity, String lemma) {
        lemmaEntity.setSiteId(siteEntity);
        lemmaEntity.setLemma(lemma);
        lemmaEntity.setFrequency(1);
        lemmaEntitySet.add(lemmaEntity);
    }

    private void fetchLinks(Elements elements) {
        List<LinksParser> taskList = new ArrayList<>();
        for (Element link : elements) {
            String fetchedLink = URLDecoder.decode(link.absUrl("href"), StandardCharsets.UTF_8);
            if (isCorrectLink(fetchedLink, siteEntity.getUrl()) &&
                    (!refsList.isRefPresent(fetchedLink) && !IndexingServiceImpl.isStopped)) {
                refsList.addRef(fetchedLink);

                LinksParser task = new LinksParser(siteEntity, fetchedLink, refsList, siteRepository, pageRepository, lemmaRepository);
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
            log.info(AnsiColor.ANSI_PURPLE + "Status code: " + AnsiColor.ANSI_RED
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

    private void handleException(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null) {
            ex.printStackTrace();
        } else if (msg.contains("Unhandled content type")) {
            savePage(405, ref, msg, siteEntity);
        } else {
            savePage(888, ref, msg, siteEntity);
        }
    }
}