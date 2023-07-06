package searchengine.dto.statistics;

import lombok.Data;

@Data
public class TotalStatistics {
    private int sites;
    private int pages;
    private int lemmas;
    private int notFound;
    private boolean indexing;
}
