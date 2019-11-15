/*
 * Copyright 2018 National Library of Norway.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.nb.nna.veidemann.commons.db;

import no.nb.nna.veidemann.api.commons.v1.ExtractedText;
import no.nb.nna.veidemann.api.config.v1.CrawlScope;
import no.nb.nna.veidemann.api.contentwriter.v1.CrawledContent;
import no.nb.nna.veidemann.api.contentwriter.v1.StorageRef;
import no.nb.nna.veidemann.api.frontier.v1.CrawlExecutionStatus;
import no.nb.nna.veidemann.api.frontier.v1.CrawlExecutionStatusChange;
import no.nb.nna.veidemann.api.frontier.v1.CrawlLog;
import no.nb.nna.veidemann.api.frontier.v1.JobExecutionStatus;
import no.nb.nna.veidemann.api.frontier.v1.PageLog;
import no.nb.nna.veidemann.api.report.v1.CrawlExecutionsListRequest;
import no.nb.nna.veidemann.api.report.v1.CrawlLogListRequest;
import no.nb.nna.veidemann.api.report.v1.JobExecutionsListRequest;
import no.nb.nna.veidemann.api.report.v1.ListCountResponse;
import no.nb.nna.veidemann.api.report.v1.PageLogListRequest;

import java.util.Optional;

public interface ExecutionsAdapter {
    JobExecutionStatus createJobExecutionStatus(String jobId) throws DbException;

    JobExecutionStatus getJobExecutionStatus(String jobExecutionId) throws DbException;

    ChangeFeed<JobExecutionStatus> listJobExecutionStatus(JobExecutionsListRequest request) throws DbException;

    /**
     * Update the state for a Job Execution to ABORTED_MANUAL.
     *
     * @param jobExecutionId id of the job execution to update
     */
    JobExecutionStatus setJobExecutionStateAborted(String jobExecutionId) throws DbException;

    CrawlExecutionStatus createCrawlExecutionStatus(String jobId, String jobExecutionId, String seedId, CrawlScope scope) throws DbException;

    CrawlExecutionStatus updateCrawlExecutionStatus(CrawlExecutionStatusChange status) throws DbException;

    CrawlExecutionStatus getCrawlExecutionStatus(String crawlExecutionId) throws DbException;

    ChangeFeed<CrawlExecutionStatus> listCrawlExecutionStatus(CrawlExecutionsListRequest request) throws DbException;

    /**
     * Update the state for a Crawl Execution to ABORTED_MANUAL.
     * <p>
     * The frontier should detect this and abort the crawl.
     *
     * @param crawlExecutionId id of the execution to update
     */
    CrawlExecutionStatus setCrawlExecutionStateAborted(String crawlExecutionId) throws DbException;

    Optional<CrawledContent> hasCrawledContent(CrawledContent cc) throws DbException;

    StorageRef saveStorageRef(StorageRef storageRef) throws DbException;

    StorageRef getStorageRef(String warcId) throws DbException;

    CrawlLog saveCrawlLog(CrawlLog cl) throws DbException;

    ChangeFeed<CrawlLog> listCrawlLogs(CrawlLogListRequest request) throws DbException;

    ListCountResponse countCrawlLogs(CrawlLogListRequest request) throws DbException;

    PageLog savePageLog(PageLog pageLog) throws DbException;

    ChangeFeed<PageLog> listPageLogs(PageLogListRequest request) throws DbException;

    ListCountResponse countPageLogs(PageLogListRequest request) throws DbException;

    ExtractedText addExtractedText(ExtractedText et) throws DbException;

    /**
     * Set the desired pause state for Veidemann
     *
     * @param value true if Veidemann should pause
     * @return the old state
     * @throws DbException
     */
    boolean setDesiredPausedState(boolean value) throws DbException;

    /**
     * Get the desired pause state for Veidemann
     *
     * @return true if Veidemann should pause
     * @throws DbException
     */
    boolean getDesiredPausedState() throws DbException;

    /**
     * Get the actual pause state for Veidemann
     *
     * @return true if Veidemann is paused
     * @throws DbException
     */
    boolean isPaused() throws DbException;
}
