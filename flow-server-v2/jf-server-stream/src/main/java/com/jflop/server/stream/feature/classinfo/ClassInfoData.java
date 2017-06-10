package com.jflop.server.stream.feature.classinfo;

import java.util.List;
import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 03/06/2017
 */
public class ClassInfoData {

    public Map<String, Map<String, List<String>>> classMethodSignatures;

    public ClassInfoData() {
    }

    public ClassInfoData(Map<String, Map<String, List<String>>> classMethodSignatures) {
        this.classMethodSignatures = classMethodSignatures;
    }
}
