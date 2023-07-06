package searchengine.dto.statistics;

import lombok.Data;

@Data
public class DetailedStatisticsItem {
    private String name;
    private String url;
    private String status;
    private long statusTime;
    private int pages;
    private int lemmas;
    private String error;
    private int notFound;
    private int indexingTime;
}
