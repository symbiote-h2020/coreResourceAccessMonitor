package eu.h2020.symbiote.cram.util;

import eu.h2020.symbiote.cram.managers.AuthorizationManager;
import eu.h2020.symbiote.cram.messaging.AccessNotificationListener;
import eu.h2020.symbiote.cram.model.NextPopularityUpdate;
import eu.h2020.symbiote.cram.repository.ResourceRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Timer;

/**
 * Created by vasgl on 7/2/2017.
 */
@Component
public class ResourceAccessStatsUpdater {

    private static Log log = LogFactory.getLog(ResourceAccessStatsUpdater.class);

    private ResourceRepository resourceRepository;
    private NextPopularityUpdate nextPopularityUpdate;
    private Long subIntervalDuration;
    private Long noSubIntervals;
    private AccessNotificationListener accessNotificationListener;
    private Timer timer;
    private ScheduledUpdate scheduledUpdate;
    private PopularityUpdater popularityUpdater;
    private AuthorizationManager authorizationManager;

    @Autowired
    public ResourceAccessStatsUpdater(ResourceRepository resourceRepository, NextPopularityUpdate nextPopularityUpdate,
                                      @Qualifier("subIntervalDuration") Long subIntervalDuration,
                                      @Qualifier("noSubIntervals") Long noSubIntervals,
                                      AccessNotificationListener accessNotificationListener,
                                      PopularityUpdater popularityUpdater, AuthorizationManager authorizationManager) {
        Assert.notNull(resourceRepository,"Resource repository can not be null!");
        this.resourceRepository = resourceRepository;

        Assert.notNull(nextPopularityUpdate,"nextPopularityUpdate can not be null!");
        this.nextPopularityUpdate = nextPopularityUpdate;

        Assert.notNull(subIntervalDuration,"subIntervalDuration can not be null!");
        this.subIntervalDuration = subIntervalDuration;

        Assert.notNull(noSubIntervals,"noSubIntervals can not be null!");
        this.noSubIntervals = noSubIntervals;

        Assert.notNull(accessNotificationListener,"accessNotificationListener can not be null!");
        this.accessNotificationListener = accessNotificationListener;

        Assert.notNull(popularityUpdater,"PopularityUpdater can not be null!");
        this.popularityUpdater = popularityUpdater;

        Assert.notNull(authorizationManager,"AuthorizationManager can not be null!");
        this.authorizationManager = authorizationManager;

        this.timer = new Timer();
        startTimer();
    }

    public Timer getTimer() { return this.timer; }
    public ScheduledUpdate getScheduledUpdate() { return this.scheduledUpdate; }

    public void restartTimer() {
        cancelTimer();
        startTimer();
    }

    public void startTimer() {
        timer = new Timer();
        scheduledUpdate = new ScheduledUpdate(resourceRepository, noSubIntervals,
                subIntervalDuration, accessNotificationListener, popularityUpdater, authorizationManager);
        timer.schedule(scheduledUpdate, nextPopularityUpdate.getNextUpdate(), subIntervalDuration);
    }

    public void cancelTimer() {
        timer.cancel();
        timer.purge();
        scheduledUpdate.cancel();
    }

    public NextPopularityUpdate getNextPopularityUpdate() {
        return nextPopularityUpdate;
    }

    public void setNextPopularityUpdate(NextPopularityUpdate nextPopularityUpdate) {
        this.nextPopularityUpdate = nextPopularityUpdate;
    }
}
