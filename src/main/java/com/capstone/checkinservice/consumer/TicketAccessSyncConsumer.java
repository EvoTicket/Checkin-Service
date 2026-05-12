package com.capstone.checkinservice.consumer;

import com.capstone.checkinservice.dto.event.TicketAccessSyncEvent;
import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketAccessSyncConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TicketAccessStateRepository ticketAccessStateRepository;
    private final ObjectMapper objectMapper;

    private static final String STREAM_KEY = "ticket-access-sync";
    private static final String CONSUMER_GROUP = "checkin-service-group";
    private static final String CONSUMER_NAME = "checkin-sync-worker";

    private Subscription subscription;

    @PostConstruct
    public void init() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, CONSUMER_GROUP);
            log.info("Created consumer group '{}' for stream '{}'", CONSUMER_GROUP, STREAM_KEY);
        } catch (Exception e) {
            log.debug("Consumer group '{}' already exists for stream '{}'", CONSUMER_GROUP, STREAM_KEY);
        }

        this.subscription = listenerContainer.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                this);

        listenerContainer.start();
        log.info("Started TicketAccessSyncConsumer for stream '{}'", STREAM_KEY);
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            String body = message.getValue().get("payload");
            log.info("Received ticket sync event: {}", body);

            TicketAccessSyncEvent event = objectMapper.readValue(body, TicketAccessSyncEvent.class);
            handleSyncEvent(event);

            redisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, message.getId());
        } catch (Exception e) {
            log.error("Error processing ticket sync message", e);
        }
    }

    private void handleSyncEvent(TicketAccessSyncEvent event) {
        TicketAccessState state = ticketAccessStateRepository.findByTicketAssetId(event.getTicketAssetId())
                .orElseGet(() -> TicketAccessState.builder().ticketAssetId(event.getTicketAssetId()).build());

        state.setTicketCode(event.getTicketCode());
        state.setEventId(event.getEventId());
        state.setShowtimeId(event.getShowtimeId());
        state.setTicketTypeName(event.getTicketTypeName());
        state.setZoneLabel(event.getZoneLabel());
        state.setSeatLabel(event.getSeatLabel());
        state.setCurrentOwnerId(event.getCurrentOwnerId());
        state.setQrVersion(event.getQrVersion());
        state.setAccessStatus(event.getAccessStatus());
        state.setGatePolicySnapshot(event.getGatePolicySnapshot());
        
        // If it's a new record or update, set the allowed gates based on policy
        if (state.getAllowedGateIds() == null) {
            state.setAllowedGateIds(event.getGatePolicySnapshot());
        }

        ticketAccessStateRepository.save(state);
        log.info("Synchronized TicketAccessState for assetId: {}", event.getTicketAssetId());
    }

    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.cancel();
        }
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }
}
