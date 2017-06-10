package com.jflop.server.processing;

import com.jflop.server.persistency.ESClient;
import com.jflop.server.runtime.MetadataIndex;
import com.jflop.server.runtime.data.ThreadMetadata;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.xpack.watcher.actions.ActionBuilders;
import org.elasticsearch.xpack.watcher.actions.logging.LoggingAction;
import org.elasticsearch.xpack.watcher.client.WatchSourceBuilder;
import org.elasticsearch.xpack.watcher.client.WatchSourceBuilders;
import org.elasticsearch.xpack.watcher.condition.CompareCondition;
import org.elasticsearch.xpack.watcher.input.search.SearchInput;
import org.elasticsearch.xpack.watcher.support.search.WatcherSearchTemplateRequest;
import org.elasticsearch.xpack.watcher.transport.actions.delete.DeleteWatchResponse;
import org.elasticsearch.xpack.watcher.transport.actions.put.PutWatchResponse;
import org.elasticsearch.xpack.watcher.trigger.schedule.Schedules;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.elasticsearch.xpack.watcher.actions.ActionBuilders.loggingAction;
import static org.elasticsearch.xpack.watcher.trigger.TriggerBuilders.schedule;
import static org.elasticsearch.xpack.watcher.trigger.schedule.IntervalSchedule.Interval.Unit.SECONDS;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 4/15/17
 */
@Component
public class AdjustInstrumentation implements InitializingBean, DisposableBean {

    private static final String WATCH_ID = "jf-adjust-instrumentation";
    private static final int INTERVAL_SEC = 10;

    @Autowired
    private ESClient client;

    @Autowired
    private MetadataIndex metadataIndex;

    @Override
    public void afterPropertiesSet() throws Exception {
        createWatch();
    }

    @Override
    public void destroy() throws Exception {
        deleteWatch();
    }

    PutWatchResponse createWatch() {
        WatchSourceBuilder watchSourceBuilder = WatchSourceBuilders.watchBuilder();

        // Set the trigger
        watchSourceBuilder.trigger(schedule(Schedules.interval(INTERVAL_SEC, SECONDS)));

        // Create the search request to use for the input
        String indexName = metadataIndex.indexName();
        String docType = metadataIndex.getDocType(ThreadMetadata.class);
        SearchRequest request = Requests.searchRequest(indexName).types(docType)
                .source(searchSource()
                        .query(rangeQuery("time").lte("now").gte("now-" + INTERVAL_SEC + "s")));

        // Create the search input
        SearchInput input = new SearchInput(new WatcherSearchTemplateRequest(new String[]{indexName}, new String[]{docType}, SearchType.DEFAULT,
                WatcherSearchTemplateRequest.DEFAULT_INDICES_OPTIONS, new BytesArray(request.source().toString())), null, null, null);

        // Set the input
        watchSourceBuilder.input(input);

        // Set the condition
        watchSourceBuilder.condition(new CompareCondition("ctx.payload.hits.total", CompareCondition.Op.GT, 0));

        // Create the logging action
        LoggingAction.Builder action = ActionBuilders.loggingAction("Found {{ctx.payload.hits.total}} threads");
        watchSourceBuilder.addAction("log", action);

        PutWatchResponse putWatchResponse = client.getWatcherClient().preparePutWatch(WATCH_ID)
                .setSource(watchSourceBuilder)
                .get();
        return putWatchResponse;

/*
        ///////////////////////
        client.getWatcherClient().preparePutWatch(WATCH_ID)
                .setSource(watchBuilder()
                        .trigger(schedule(Schedules.interval(INTERVAL_SEC, SECONDS)))
                        .input(searchInput(new WatcherSearchTemplateRequest(
                                new String[]{indexName}, new String[]{docType}, SearchType.DEFAULT,
                                WatcherSearchTemplateRequest.DEFAULT_INDICES_OPTIONS,
                                searchSource()
                                        .query(rangeQuery("time").lte("now").gte("now-" + INTERVAL_SEC + "s")
                                        ).buildAsBytes())))
                        .condition(new CompareCondition("ctx.payload.hits.total", CompareCondition.Op.GT, 0L))
                        .addAction("log", loggingAction("Found {{ctx.payload.hits.total}} threads"))
                ).get();
*/
    }


    DeleteWatchResponse deleteWatch() {
        return client.getWatcherClient().prepareDeleteWatch(WATCH_ID).get();
    }

}
