/*
 * Copyright (C) 2016 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
 * Karlsruhe, Germany.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.fraunhofer.iosb.ilt.frostserver.mqtt;

import de.fraunhofer.iosb.ilt.frostserver.messagebus.MessageListener;
import de.fraunhofer.iosb.ilt.frostserver.model.EntityChangedMessage;
import de.fraunhofer.iosb.ilt.frostserver.model.EntityType;
import de.fraunhofer.iosb.ilt.frostserver.model.ModelRegistry;
import de.fraunhofer.iosb.ilt.frostserver.model.core.Entity;
import de.fraunhofer.iosb.ilt.frostserver.mqtt.create.EntityCreateEvent;
import de.fraunhofer.iosb.ilt.frostserver.mqtt.create.EntityCreateListener;
import de.fraunhofer.iosb.ilt.frostserver.mqtt.subscription.Subscription;
import de.fraunhofer.iosb.ilt.frostserver.mqtt.subscription.SubscriptionEvent;
import de.fraunhofer.iosb.ilt.frostserver.mqtt.subscription.SubscriptionFactory;
import de.fraunhofer.iosb.ilt.frostserver.mqtt.subscription.SubscriptionListener;
import de.fraunhofer.iosb.ilt.frostserver.path.Version;
import de.fraunhofer.iosb.ilt.frostserver.persistence.PersistenceManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.PersistenceManagerFactory;
import de.fraunhofer.iosb.ilt.frostserver.property.Property;
import de.fraunhofer.iosb.ilt.frostserver.service.RequestTypeUtils;
import de.fraunhofer.iosb.ilt.frostserver.service.Service;
import de.fraunhofer.iosb.ilt.frostserver.service.ServiceRequestBuilder;
import de.fraunhofer.iosb.ilt.frostserver.service.ServiceResponse;
import de.fraunhofer.iosb.ilt.frostserver.settings.CoreSettings;
import de.fraunhofer.iosb.ilt.frostserver.settings.MqttSettings;
import de.fraunhofer.iosb.ilt.frostserver.settings.UnknownVersionException;
import de.fraunhofer.iosb.ilt.frostserver.util.ChangingStatusLogger;
import de.fraunhofer.iosb.ilt.frostserver.util.ProcessorHelper;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Michael Jacoby
 * @author scf
 */
public class MqttManager implements SubscriptionListener, MessageListener, EntityCreateListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttManager.class);

    private final Map<EntityType, SubscriptionManager> subscriptions = new HashMap<>();
    private final CoreSettings settings;
    private final SubscriptionFactory subscriptionFactory;

    private MqttServer server;
    private BlockingQueue<EntityChangedMessage> entityChangedEventQueue;
    private ExecutorService entityChangedExecutorService;
    private BlockingQueue<EntityCreateEvent> entityCreateEventQueue;
    private ExecutorService entityCreateExecutorService;

    private final ChangingStatusLogger statusLogger = new ChangingStatusLogger(LOGGER);
    private final AtomicInteger topicCount = new AtomicInteger();
    private final AtomicInteger entityChangedQueueSize = new AtomicInteger();
    private final AtomicInteger entityCreateQueueSize = new AtomicInteger();
    private final LoggingStatus logStatus = new LoggingStatus();

    private boolean enabledMqtt = false;
    private boolean shutdown = false;

    public MqttManager(CoreSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("setting must be non-null");
        }
        this.settings = settings;
        subscriptionFactory = new SubscriptionFactory(settings);

        init();
    }

    private void init() {
        final ModelRegistry modelRegistry = settings.getModelRegistry();
        for (EntityType entityType : modelRegistry.getEntityTypes()) {
            subscriptions.put(entityType, new SubscriptionManager(entityType, this, topicCount));
        }

        MqttSettings mqttSettings = settings.getMqttSettings();
        if (mqttSettings.isEnableMqtt()) {
            enabledMqtt = true;
            shutdown = false;
            entityChangedEventQueue = new ArrayBlockingQueue<>(mqttSettings.getSubscribeMessageQueueSize());
            // start watching for EntityChangedEvents
            entityChangedExecutorService = ProcessorHelper.createProcessors(
                    mqttSettings.getSubscribeThreadPoolSize(),
                    entityChangedEventQueue,
                    this::handleEntityChangedEvent,
                    "Mqtt-EntityChangedProcessor");
            // start watching for EntityCreateEvents
            entityCreateEventQueue = new ArrayBlockingQueue<>(mqttSettings.getCreateMessageQueueSize());
            entityCreateExecutorService = ProcessorHelper.createProcessors(
                    mqttSettings.getCreateThreadPoolSize(),
                    entityCreateEventQueue,
                    this::handleEntityCreateEvent,
                    "Mqtt-EntityCreateProcessor");
            // start MQTT server
            server = MqttServerFactory.getInstance().get(settings);
            server.addSubscriptionListener(this);
            server.addEntityCreateListener(this);
            server.start();
            long queueLoggingInterval = settings.getSettings().getInt(CoreSettings.TAG_QUEUE_LOGGING_INTERVAL, CoreSettings.class);
            if (queueLoggingInterval > 0) {
                statusLogger
                        .setLogIntervalMs(queueLoggingInterval)
                        .addLogStatus(logStatus)
                        .start();
            }
        } else {
            enabledMqtt = false;
            entityChangedExecutorService = null;
            entityChangedEventQueue = new ArrayBlockingQueue<>(1);
            entityCreateExecutorService = null;
            entityCreateEventQueue = new ArrayBlockingQueue<>(1);
            server = null;
        }
    }

    public void shutdown() {
        shutdown = true;
        statusLogger.stop();
        ProcessorHelper.shutdownProcessors(entityChangedExecutorService, entityChangedEventQueue, 10, TimeUnit.SECONDS);
        ProcessorHelper.shutdownProcessors(entityCreateExecutorService, entityCreateEventQueue, 10, TimeUnit.SECONDS);
        if (server != null) {
            server.stop();
        }
    }

    private void handleEntityChangedEvent(EntityChangedMessage message) {
        logStatus.setEntityChangedQueueSize(entityChangedQueueSize.decrementAndGet());
        final EntityChangedMessage.Type eventType = message.getEventType();
        EntityType entityType = message.getEntityType();
        LOGGER.trace("Received a {} message for a {}.", eventType, entityType);
        if (eventType == EntityChangedMessage.Type.DELETE) {
            // v1.0 does not do delete notification.
            return;
        }
        // check if there is any subscription, if not do not publish at all
        if (!subscriptions.containsKey(entityType)) {
            return;
        }

        Entity entity = message.getEntity();
        Set<Property> fields = message.getFields();
        try (PersistenceManager persistenceManager = PersistenceManagerFactory.getInstance(settings).create()) {
            subscriptions.get(entityType).handleEntityChanged(persistenceManager, entity, fields);
        } catch (Exception ex) {
            LOGGER.error("error handling MQTT subscriptions", ex);
        }
    }

    public void notifySubscription(Subscription subscription, Entity entity) {
        final String topic = subscription.getTopic();
        try {
            String payload = subscription.formatMessage(entity);
            server.publish(topic, payload, settings.getMqttSettings().getQosLevel());
        } catch (IOException ex) {
            LOGGER.error("publishing to MQTT on topic '{}' failed", topic, ex);
        }
    }

    private void handleEntityCreateEvent(EntityCreateEvent e) {
        logStatus.setEntityCreateQueueSize(entityCreateQueueSize.decrementAndGet());
        String topic = e.getTopic();
        Version version;
        try {
            version = getVersionFromTopic(topic);
        } catch (UnknownVersionException ex) {
            LOGGER.info("received message on topic '{}' which contains no version info.", topic);
            return;
        }

        String url = topic.replaceFirst(version.urlPart, "");
        try (Service service = new Service(settings)) {
            ServiceResponse<? extends Entity> response = service.execute(new ServiceRequestBuilder(version)
                    .withRequestType(RequestTypeUtils.CREATE)
                    .withContent(e.getPayload())
                    .withUrlPath(url)
                    .build());
            if (response.isSuccessful()) {
                LOGGER.debug("Entity (ID {}) created via MQTT", response.getResult().getId().getValue());
            } else {
                LOGGER.error("Creating entity via MQTT failed (topic: {}, payload: {}, code: {}, message: {})",
                        topic, e.getPayload(), response.getCode(), response.getMessage());
            }
        }
    }

    private void entityChanged(EntityChangedMessage e) {
        if (shutdown || !enabledMqtt) {
            return;
        }
        if (entityChangedEventQueue.offer(e)) {
            logStatus.setEntityChangedQueueSize(entityChangedQueueSize.incrementAndGet());
        } else {
            LOGGER.warn("EntityChangedevent discarded because message queue is full {}! Increase mqtt.SubscribeMessageQueueSize and/or mqtt.SubscribeThreadPoolSize.", entityChangedEventQueue.size());
        }
    }

    @Override
    public void onSubscribe(SubscriptionEvent e) {
        Subscription subscription = subscriptionFactory.get(e.getTopic());
        if (subscription == null) {
            // Not a valid topic.
            return;
        }

        subscriptions.get(subscription.getEntityType())
                .addSubscription(subscription);
        logStatus.setTopicCount(topicCount.get());
    }

    @Override
    public void onUnsubscribe(SubscriptionEvent e) {
        Subscription subscription = subscriptionFactory.get(e.getTopic());
        if (subscription == null) {
            // Not a valid topic.
            return;
        }
        subscriptions.get(subscription.getEntityType())
                .removeSubscription(subscription);
        logStatus.setTopicCount(topicCount.get());
    }

    @Override
    public void messageReceived(EntityChangedMessage message) {
        entityChanged(message);
    }

    @Override
    public void onEntityCreate(EntityCreateEvent e) {
        if (shutdown || !enabledMqtt) {
            return;
        }
        if (entityCreateEventQueue.offer(e)) {
            logStatus.setEntityCreateQueueSize(entityCreateQueueSize.incrementAndGet());
        } else {
            LOGGER.warn("EntityCreateEvent discarded because message queue is full {}! Increase mqtt.SubscribeMessageQueueSize and/or mqtt.SubscribeThreadPoolSize", entityCreateEventQueue.size());
        }
    }

    public static Version getVersionFromTopic(String topic) throws UnknownVersionException {
        int pos = topic.indexOf('/');
        if (pos == -1) {
            throw new UnknownVersionException("Could not find version in topic " + topic);
        }
        String versionString = topic.substring(0, pos);
        Version version = Version.forString(versionString);
        if (version == null) {
            throw new UnknownVersionException("Could not find version in topic " + topic);
        }
        return version;
    }

    private static class LoggingStatus extends ChangingStatusLogger.ChangingStatusDefault {

        public static final String MESSAGE = "entityCreateQueue: {}, entityChangedQueue: {}, topics: {}";
        public final Object[] status;

        public LoggingStatus() {
            super(MESSAGE, new Object[3]);
            status = getCurrentParams();
            Arrays.setAll(status, (int i) -> 0);
        }

        public LoggingStatus setEntityCreateQueueSize(Integer size) {
            status[0] = size;
            return this;
        }

        public LoggingStatus setEntityChangedQueueSize(Integer size) {
            status[1] = size;
            return this;
        }

        public LoggingStatus setTopicCount(Integer count) {
            status[2] = count;
            return this;
        }

    }
}
