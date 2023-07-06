package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SitesList sites;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        int sitesCount = siteRepository.sitesCount();
        total.setSites(sitesCount);
        total.setIndexing(true); // что это и где взять результат окончания индексации?

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
        if (sitesCount != 0) {
            for (Site site : sitesList) {
                DetailedStatisticsItem item = new DetailedStatisticsItem();
                item.setName(site.getName());
                item.setUrl(site.getUrl());

                SiteEntity siteEntity = siteRepository.site(site.getUrl());
                item.setStatus(String.valueOf(siteEntity.getStatus()));
                item.setError(siteEntity.getLastError());
                item.setIndexingTime(siteEntity.getIndexingTime());

                ZonedDateTime zdt = ZonedDateTime.of(siteEntity.getStatusTime(), ZoneId.systemDefault());
                long dateLong = zdt.toInstant().toEpochMilli();
                item.setStatusTime(dateLong);

                int pages = pageRepository.pagesCount(siteEntity.getId());
                item.setPages(pages);

                int lemmas = lemmaRepository.lemmaCount(siteEntity.getId());
                item.setLemmas(lemmas);

                int notFound = pageRepository.httpNotFound(siteEntity.getId());
                item.setNotFound(notFound);

                total.setPages(total.getPages() + pages);
                total.setLemmas(total.getLemmas() + lemmas);
                total.setNotFound(total.getNotFound() + notFound);
                detailed.add(item);
            }
        } else {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName("Site name");
            item.setUrl("http://somesite.info");
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true); // что это?
        return response;
    }
}
