package eu.h2020.symbiote.cram.model;

import java.util.Date;

/**
 * Created by vasgl on 6/30/2017.
 */
public class NextPopularityUpdate extends CramPersistentVariables {

    private Date nextUpdate;

    public NextPopularityUpdate() {
        // empty constructor
    }

    public NextPopularityUpdate(Long subIntervalDuration) {

        this.nextUpdate = new Date(new Date().getTime() + subIntervalDuration);
        this.variableName = "NEXT_POPULARITY_UPDATE";
    }

    public Date getNextUpdate() { return this.nextUpdate; }
    public void setNextUpdate(Date timestamp) { this.nextUpdate = timestamp; }
}
