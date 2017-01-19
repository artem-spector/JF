package com.jflop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.persistency.ESClient;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.runtime.data.FlowMetadata;

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
        new TestUtil().copyFlowsToFiles();
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

    private <T> T retrieve(String index, String doctype, String id, Class<T> valueType) {
        PersistentData<T> doc = esClient.getDocument(index, doctype, new PersistentData<>(id, 0), valueType);
        if (doc == null)
            throw new RuntimeException("Document does not exist: " + index + "/" + doctype + "/" + id);
        return doc.source;
    }

    private void saveAsJson(Object value, String folderName, String fileName) throws IOException {
        File folder = new File(folderName);
        if (!folder.exists() && !folder.mkdirs())
            throw new IOException("Failed to locate or create folder " + folder.getAbsolutePath());
        if (!folder.isDirectory())
            throw new IOException("Not a folder: " + folder.getAbsolutePath());


        File file = new File(folder, fileName);
        FileOutputStream out = new FileOutputStream(file);
        mapper.writeValue(out, value);
        out.flush();
        out.close();
        System.out.println("file saved: " + file.getAbsolutePath());
    }

    public static <T> T readFromFile(String fileName, Class<T> valueType) throws IOException {
        File in = new File(fileName);
        return mapper.readValue(in, valueType);
    }
}
