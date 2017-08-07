package eu.h2020.symbiote.cram.integration;

import eu.h2020.symbiote.core.cci.accessNotificationMessages.NotificationMessage;
import eu.h2020.symbiote.core.cci.accessNotificationMessages.SuccessfulAccessMessageInfo;
import eu.h2020.symbiote.core.cci.accessNotificationMessages.SuccessfulPushesMessageInfo;
import eu.h2020.symbiote.core.internal.popularity.PopularityUpdate;
import eu.h2020.symbiote.cram.aams.SearchEngineListener;
import eu.h2020.symbiote.cram.model.CramResource;
import eu.h2020.symbiote.cram.model.SubIntervalViews;
import eu.h2020.symbiote.cram.repository.ResourceRepository;
import eu.h2020.symbiote.cram.util.PopularityUpdater;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

/**
 * Created by vasgl on 7/3/2017.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.sleuth.enabled=false",
        "symbiote.testaam" + ".url=http://localhost:8080",
        "symbiote.coreaam.url=http://localhost:8080",
        "platform.aam.url=http://localhost:8080",
        "subIntervalDuration=100000",
        "intervalDuration=2000000",
        "informSearchInterval=1000",
        "symbiote.core.cram.database=symbiote-core-cram-database-put",
        "rabbit.queueName.cram.getResourceUrls=cramGetResourceUrls-put",
        "rabbit.routingKey.cram.getResourceUrls=symbIoTe.CoreResourceAccessMonitor.coreAPI.get_resource_urls-put",
        "rabbit.queueName.cram.accessNotifications=accessNotifications-put",
        "rabbit.routingKey.cram.accessNotifications=symbIoTe.CoreResourceAccessMonitor.coreAPI.accessNotifications-put",
        "rabbit.queueName.search.popularityUpdates=symbIoTe-search-popularityUpdatesReceived-put"})
@ContextConfiguration
@Configuration
@ComponentScan
@EnableAutoConfiguration
public class PopularityUpdaterTests {


    private static final Logger log = LoggerFactory
            .getLogger(PopularityUpdaterTests.class);

    @Autowired
    private ResourceRepository resourceRepo;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private SearchEngineListener searchEngineListener;

    @Autowired
    @Qualifier("informSearchInterval")
    private Long informSearchInterval;

    @Autowired
    private PopularityUpdater popularityUpdater;

    @Value("${rabbit.exchange.cram.name}")
    private String cramExchangeName;

    @Value("${rabbit.routingKey.cram.accessNotifications}")
    private String cramAccessNotificationsRoutingKey;

    @Value("${platform.aam.url}")
    private String platformAAMUrl;

    private String resourceUrl;

    // Execute the Setup method before the test.
    @Before
    public void setUp() {
        List<String> observedProperties = Arrays.asList("temp", "air");
        resourceUrl = platformAAMUrl + "/rap";

        CramResource resource1 = new CramResource();
        resource1.setId("sensor_id_put");
        resource1.setInterworkingServiceURL(platformAAMUrl);
        resource1.setResourceUrl(resourceUrl);
        resource1.setViewsInDefinedInterval(0);
        ArrayList<SubIntervalViews> subIntervals = new ArrayList<>();
        SubIntervalViews subInterval1 = new SubIntervalViews(new Date(1000), new Date(2000), 0);
        subIntervals.add(subInterval1);
        resource1.setViewsInSubIntervals(subIntervals);

        CramResource resource2 = new CramResource();
        resource2.setId("sensor_id2_put");
        resource2.setInterworkingServiceURL(platformAAMUrl);
        resource2.setResourceUrl(resourceUrl);
        resource2.setViewsInDefinedInterval(0);
        resource2.setViewsInSubIntervals(subIntervals);

        resourceRepo.save(resource1);
        resourceRepo.save(resource2);

        popularityUpdater.restartTimer();
    }

    @After
    public void clearSetup() {
        resourceRepo.deleteAll();
        searchEngineListener.clearRequestsReceivedByListener();
    }

    @Test
    public void testPopularityUpdate() throws Exception {
        log.info("testPopularityUpdate STARTED");

        NotificationMessage notificationMessage = createSuccessfulAttemptsMessage();
        rabbitTemplate.convertAndSend(cramExchangeName, cramAccessNotificationsRoutingKey, notificationMessage);

        // Sleep to make sure that message has been received
        TimeUnit.MILLISECONDS.sleep((long) (0.55 * informSearchInterval));

        assertEquals(2, popularityUpdater.getPopularityUpdatesMap().size());

        while(searchEngineListener.popularityUpdatesMessagesReceived() < 1) {
            TimeUnit.MILLISECONDS.sleep(100);
        }
        // Added extra delay to make sure that the message is handled
        TimeUnit.MILLISECONDS.sleep(100);

        assertEquals(0, popularityUpdater.getPopularityUpdatesMap().size());
        assertEquals(1, searchEngineListener.popularityUpdatesMessagesReceived());
        assertEquals(2, searchEngineListener.getPopularityUpdatesMessages().get(0).getPopularityUpdateList().size());

        for (PopularityUpdate popularityUpdate : searchEngineListener.getPopularityUpdatesMessages()
                .get(0).getPopularityUpdateList()) {

            if (popularityUpdate.getId().equals("sensor_id_put")) {
                assertEquals(4, (long)popularityUpdate.getViewsInDefinedInterval());
                continue;
            }

            if (popularityUpdate.getId().equals("sensor_id2_put")) {
                assertEquals(4, (long) popularityUpdate.getViewsInDefinedInterval());
                continue;
            }

            fail("Code should not reach this point");
        }

        // Repeat without sending notifications for sensor_id_put
        notificationMessage.getSuccessfulAttempts().remove(0);
        notificationMessage.getSuccessfulPushes().remove(0);
        rabbitTemplate.convertAndSend(cramExchangeName, cramAccessNotificationsRoutingKey, notificationMessage);

        // Sleep to make sure that message has been received
        TimeUnit.MILLISECONDS.sleep((long) (0.55 * informSearchInterval));

        assertEquals(1, popularityUpdater.getPopularityUpdatesMap().size());

        while(searchEngineListener.popularityUpdatesMessagesReceived() < 2) {
            TimeUnit.MILLISECONDS.sleep(100);
        }
        // Added extra delay to make sure that the message is handled
        TimeUnit.MILLISECONDS.sleep(100);

        assertEquals(0, popularityUpdater.getPopularityUpdatesMap().size());
        assertEquals(2, searchEngineListener.popularityUpdatesMessagesReceived());
        assertEquals(1, searchEngineListener.getPopularityUpdatesMessages().get(1).getPopularityUpdateList().size());

        for (PopularityUpdate popularityUpdate : searchEngineListener.getPopularityUpdatesMessages()
                .get(1).getPopularityUpdateList()) {

            if (popularityUpdate.getId().equals("sensor_id2_put")) {
                assertEquals(8, (long) popularityUpdate.getViewsInDefinedInterval());
                continue;
            }

            fail("Code should not reach this point");
        }

        log.info("testPopularityUpdate ENDED");
    }

    private NotificationMessage createSuccessfulAttemptsMessage() {
        ArrayList<Date> dateList = new ArrayList<Date>();
        dateList.add(new Date(1000));
        dateList.add(new Date(1400));
        dateList.add(new Date(20000));

        SuccessfulAccessMessageInfo successfulAttempts1 = new SuccessfulAccessMessageInfo();
        successfulAttempts1.setSymbIoTeId("sensor_id_put");
        successfulAttempts1.setTimestamps(dateList);

        SuccessfulAccessMessageInfo successfulAttempts2 = new SuccessfulAccessMessageInfo();
        successfulAttempts2.setSymbIoTeId("sensor_id2_put");
        successfulAttempts2.setTimestamps(dateList);

        SuccessfulPushesMessageInfo successfulPushes1 = new SuccessfulPushesMessageInfo();
        successfulPushes1.setSymbIoTeId("sensor_id_put");
        successfulPushes1.setTimestamps(dateList);

        SuccessfulPushesMessageInfo successfulPushes2 = new SuccessfulPushesMessageInfo();
        successfulPushes2.setSymbIoTeId("sensor_id2_put");
        successfulPushes2.setTimestamps(dateList);

        NotificationMessage notificationMessage = new NotificationMessage();
        notificationMessage.addSuccessfulAttempt(successfulAttempts1);
        notificationMessage.addSuccessfulAttempt(successfulAttempts2);
        notificationMessage.addSuccessfulPush(successfulPushes1);
        notificationMessage.addSuccessfulPush(successfulPushes2);

        return notificationMessage;
    }
}
