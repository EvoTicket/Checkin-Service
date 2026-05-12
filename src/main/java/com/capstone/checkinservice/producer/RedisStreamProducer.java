package com.capstone.checkinservice.producer;

import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStreamProducer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void sendMessage(String streamKey, Object message) {
        try {
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("payload", objectMapper.writeValueAsString(message));
            messageMap.put("timestamp", String.valueOf(System.currentTimeMillis()));

            ObjectRecord<String, Map<String, String>> objectRecord = StreamRecords
                    .newRecord()
                    .ofObject(messageMap)
                    .withStreamKey(streamKey);

            redisTemplate.opsForStream().add(objectRecord);

            log.info("Message sent to stream '{}': {}", streamKey, message);
        } catch (Exception e) {
            log.error("Error sending message to stream '{}'", streamKey, e);
            throw new RuntimeException("Failed to send message to Redis Stream", e);
        }
    }
}
