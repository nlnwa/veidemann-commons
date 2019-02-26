package no.nb.nna.veidemann.commons.db;

import com.google.protobuf.Timestamp;
import no.nb.nna.veidemann.api.frontier.v1.CrawlHostGroup;
import no.nb.nna.veidemann.api.frontier.v1.QueuedUri;

public interface CrawlQueueAdapter {
    long deleteQueuedUrisForExecution(String executionId) throws DbException;

    long queuedUriCount(String executionId) throws DbException;

    boolean uriNotIncludedInQueue(QueuedUri qu, Timestamp since) throws DbException;

    CrawlQueueFetcher getCrawlQueueFetcher();

//    FutureOptional<QueuedUri> getNextQueuedUriToFetch(CrawlHostGroup crawlHostGroup) throws DbException;

    QueuedUri addToCrawlHostGroup(QueuedUri qUri) throws DbException;

//    FutureOptional<CrawlHostGroup> borrowFirstReadyCrawlHostGroup() throws DbException;

    void releaseCrawlHostGroup(CrawlHostGroup crawlHostGroup, long nextFetchDelayMs) throws DbException;

}
