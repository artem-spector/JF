package com.jflop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.persistency.ESClient;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.processed.FlowSummary;
import com.jflop.server.runtime.data.processed.MethodCall;
import com.jflop.server.runtime.data.processed.MethodFlow;

import java.io.*;

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
//        util.copyFlowsToFiles();
        util.copyFlowSummaryToFile();
    }

    public TestUtil() throws Exception {
        esClient = new ESClient("localhost", 9300);
    }

    private void copyFlowsToFiles() throws IOException {
        String folderName = "src/test/resources/samples/sameFlows/1";
        String file1 = "flow1.json";
        String file2 = "flow2.json";

        String id1 = "yzZwcvbZmkpXJPiuDX/+Wh0pOGw=";
        String id2 = "yycuC3q11mOQpiM81z4K+FAvydA=";

        FlowMetadata flow1 = retrieve("jf-metadata", "flow", id1, FlowMetadata.class);
        FlowMetadata flow2 = retrieve("jf-metadata", "flow", id2, FlowMetadata.class);
        saveAsJson(flow1, folderName, file1);
        saveAsJson(flow2, folderName, file2);

        boolean res1 = FlowMetadata.maybeSame(flow1, flow2);
        System.out.println("1 same as 2: " + res1);
    }

    private void copyFlowSummaryToFile() throws IOException {
        String folderName = "src/test/resources/samples/flowSummary/4";

        String summaryId = "AVnBGqlQ7vTJEsJlop7y";
        String summaryFile = "summary1.json";

        String flowStr = "{\"name\":\"m5\",\"duration\":7,\"nested\":[{\"name\":\"m2\",\"duration\":34,\"nested\":[{\"name\":\"m1\",\"duration\":42,\"nested\":[{\"name\":\"m7\",\"duration\":17,\"nested\":[{\"name\":\"m8\",\"duration\":0},{\"name\":\"m4\",\"duration\":0},{\"name\":\"m6\",\"duration\":0}]},{\"name\":\"m3\",\"duration\":4,\"nested\":[]}]}]}]}";
        String flowFile = "generatedFlow1.json";


        FlowSummary flowSummary = retrieve("jf-processed-data", "flowSummary", summaryId, FlowSummary.class);
        saveAsJson(flowSummary, folderName, summaryFile);
        int flowCount = 1;
        for (MethodCall root : flowSummary.roots) {
            for (MethodFlow methodFlow : root.flows) {
                FlowMetadata flowMetadata = retrieve("jf-metadata", "flow", methodFlow.flowId, FlowMetadata.class);
                saveAsJson(flowMetadata, folderName, "flow" + (flowCount++) + ".json");
            }
        }

        saveString(flowStr, folderName, flowFile);
    }

    private <T> T retrieve(String index, String doctype, String id, Class<T> valueType) {
        PersistentData<T> doc = esClient.getDocument(index, doctype, new PersistentData<>(id, 0), valueType);
        if (doc == null)
            throw new RuntimeException("Document does not exist: " + index + "/" + doctype + "/" + id);
        return doc.source;
    }

    private void saveAsJson(Object value, String folderName, String fileName) throws IOException {
        File file = getFile(folderName, fileName);
        FileOutputStream out = new FileOutputStream(file);
        mapper.writeValue(out, value);
        out.flush();
        out.close();
        System.out.println("file saved: " + file.getAbsolutePath());
    }

    private void saveString(String value, String folderName, String fileName) throws IOException {
        File file = getFile(folderName, fileName);
        Writer out = new OutputStreamWriter(new FileOutputStream(file));
        out.write(value + "\n");
        out.flush();
        out.close();
        System.out.println("file saved: " + file.getAbsolutePath());
    }

    private File getFile(String folderName, String fileName) throws IOException {
        File folder = new File(folderName);
        if (!folder.exists() && !folder.mkdirs())
            throw new IOException("Failed to locate or create folder " + folder.getAbsolutePath());
        if (!folder.isDirectory())
            throw new IOException("Not a folder: " + folder.getAbsolutePath());

        return new File(folder, fileName);
    }

    public static <T> T readValueFromFile(String fileName, Class<T> valueType) throws IOException {
        File in = new File(fileName);
        return mapper.readValue(in, valueType);
    }

    public static String readStringFromFile(String fileName) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(fileName));
        String res = "";
        String line;
        while ((line = in.readLine()) != null) res += line + "\n";
        return res;
    }
}
