package com.jflop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.persistency.ESClient;
import com.jflop.server.runtime.data.metric.MetricData;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
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
        List<String> names = new ArrayList<>();
        List<Map<String, Float>> values = new ArrayList<>();

        SearchResponse response = esClient.search("jf-processed-data", "metric", QueryBuilders.matchAllQuery(), maxHits, null);
        for (SearchHit hit : response.getHits().getHits()) {
            MetricData metricData = mapper.readValue(hit.source(), MetricData.class);
            Map<String, Float> line = metricData.metrics;
            line.keySet().forEach(k -> {if (!names.contains(k)) names.add(k);});
            values.add(line);
        }

        PrintStream out = new PrintStream(file);
        out.println(names.stream().collect(Collectors.joining(" ")));
        for (Map<String, Float> line : values) {
            String lineStr = "";
            for (String name : names) {
                if (!lineStr.isEmpty()) lineStr += " ";
                Float val = line.get(name);
                if (val == null) val = 0f;
                lineStr += val;
            }
            out.println(lineStr);
        }

        out.flush();
        out.close();
        System.out.println(values.size() + " X " + names.size() + " matrix exported to file " + file.getAbsolutePath());
    }

}
