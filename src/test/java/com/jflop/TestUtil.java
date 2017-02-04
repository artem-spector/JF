package com.jflop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.persistency.ESClient;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.metric.MetricData;
import com.jflop.server.runtime.data.metric.MetricMetadata;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO: Document!
 *
 * @author artem on 13/12/2016.
 */
public class TestUtil {

    private static final String FOLDER = "target/testContinuous-temp/";

    private static ObjectMapper mapper = new ObjectMapper();

    private ESClient esClient;

    public static void main(String[] args) throws Exception {
        TestUtil util = new TestUtil();
        util.exportMetrics(new File(FOLDER + "metrics.dat"), 10000);
        Set<String> flowIds = util.exportFlowNums(new File(FOLDER + "flowNum.dat"));
        util.exportFlowMetadata(flowIds, new File(FOLDER, "flowMetadata.dat"));
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

    private Set<String> exportFlowNums(File file) throws IOException {
        Set<String> res = new HashSet<>();

        SearchResponse response = esClient.search("jf-metadata", "metric", QueryBuilders.matchAllQuery(), 2, null);
        SearchHit[] hits = response.getHits().getHits();
        if (hits.length != 1)
            throw new RuntimeException("Unexpected number of metric metadata: " + hits.length);

        MetricMetadata metadata = mapper.readValue(hits[0].source(), MetricMetadata.class);
        Map<String, Long> sourceIDs = metadata.sourceIDs;
        int count = 0;

        PrintStream out = new PrintStream(file);
        out.println("flowId flowNum");
        for (Map.Entry<String, Long> entry : sourceIDs.entrySet()) {
            String key = entry.getKey();
            String prefix = key.substring(0, key.indexOf(":"));
            String suffix = key.substring(prefix.length() + 1);
            if (suffix.equals("duration")) {
                out.println(prefix + " " + entry.getValue());
                res.add(prefix);
                count++;
            }
        }

        out.flush();
        out.close();
        System.out.println(count + " X 2 matrix exported to file " + file.getAbsolutePath());
        return res;
    }

    private void exportFlowMetadata(Set<String> flowIds, File file) throws FileNotFoundException {
        PrintStream out = new PrintStream(file);
        out.println("flowId className methodName methodDescriptor firstLine returnLine");

        int[] total = new int[] {0, 0};
        Set<String> covered = new HashSet<>();

        for (String id : flowIds) {
            PersistentData<FlowMetadata> doc = esClient.getDocument("jf-metadata", "flow", new PersistentData<>(id, 0), FlowMetadata.class);
            if (doc != null) {
                int[] count = printMetadata(doc.source.rootFlow, out, covered);
                total[0] += count[0];
                total[1] = Math.max(total[1], count[1]);
            }
        }

        out.flush();
        out.close();
        System.out.println(total[0] + " X " + total[1] + " matrix exported to file " + file.getAbsolutePath());
    }

    private int[] printMetadata(FlowMetadata.FlowElement element, PrintStream out, Set<String> covered) {
        int numSubflows = element.subflows == null ? 0 : element.subflows.size();
        if (covered.contains(element.flowId))
            return new int[]{0, 0};

        out.print(element.flowId + " " + element.className + " " + element.methodName + " " + element.methodDescriptor + " " + element.firstLine + " " + element.returnLine);
        if (numSubflows > 0) {
            for (FlowMetadata.FlowElement subflow : element.subflows) {
                out.print(" " + subflow.flowId);
            }
        }

        out.println();
        covered.add(element.flowId);
        int[] count = new int[]{1, 6 + numSubflows};

        if (numSubflows > 0) {
            for (FlowMetadata.FlowElement subflow : element.subflows) {
                int[] subCount = printMetadata(subflow, out, covered);
                count[0] += subCount[0];
                count[1] = Math.max(count[1], subCount[1]);
            }
        }

        return count;
    }

}
