package com.jflop.server.background;

import java.util.Date;

/**
 * State of analysis task
 *
 * @author artem on 09/01/2017.
 */
public class AnalysisState {

    public Date processedUntil;

    public AnalysisState() {
    }

    public AnalysisState(Date processedUntil) {
        this.processedUntil = processedUntil;
    }
}
