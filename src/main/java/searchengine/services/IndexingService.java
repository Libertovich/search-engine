package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

import java.io.UnsupportedEncodingException;

public interface IndexingService {
    IndexingResponse startIndexing();

    IndexingResponse stopIndexing();

    IndexingResponse indexPage(String page) throws UnsupportedEncodingException;

    IndexingResponse search();
}
