package com.jflop.server.background;

import java.util.Date;

/**
 * State of analysis task
 *
 * @author artem on 09/01/2017.
 */
public class AnalysisState {

    public Date processedUntil;
    public int snapshotDuration;

    public AnalysisState() {
    }

    public static AnalysisState createState() {
        AnalysisState state = new AnalysisState();
        state.processedUntil = new Date();
        state.snapshotDuration = 1;
        return state;
    }
}
