package com.jflop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.persistency.ESClient;
import com.jflop.server.runtime.data.metric.MetricData;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TODO: Document!
 *
 * @author artem on 13/12/2016.
 */
public class TestUtil {

    private static ObjectMapper mapper = new ObjectMapper();

    private ESClient esClient;

    public static void main(String[] args) throws Exception {
        TestUtil util = new TestUtil();
        util.exportMetrics(new File("target/metrics.dat"), 10000);
    }

    private TestUtil() throws Exception {
        esClient = new ESClient("localhost", 9300);
    }

    private void exportMetrics(File file, int maxHits) throws IOException {
        DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd_HH:mm:ss");

        List<String> names = new ArrayList<>();
        List<Map<String, Object>> values = new ArrayList<>();

        SearchResponse response = esClient.search("jf-processed-data", "metric", QueryBuilders.matchAllQuery(), maxHits, SortBuilders.fieldSort("time").order(SortOrder.ASC));
        for (SearchHit hit : response.getHits().getHits()) {
            MetricData metricData = mapper.readValue(hit.source(), MetricData.class);
            Map<String, Object> line = new HashMap<>(metricData.metrics);
            line.keySet().forEach(k -> {
                if (!names.contains(k)) names.add(k);
            });
            line.put("time", metricData.time);
            values.add(line);
        }

        PrintStream out = new PrintStream(file);
        out.println(names.stream().collect(Collectors.joining(" ", "time ", "")));
        for (Map<String, Object> line : values) {
            String lineStr = dateFormat.format(line.get("time"));
            for (String name : names) {
                Object val = line.get(name);
                assert val instanceof Float;
                lineStr += " " + val;
            }
            out.println(lineStr);
        }

        out.flush();
        out.close();
        System.out.println(values.size() + " X " + names.size() + " matrix exported to file " + file.getAbsolutePath());
    }

}
