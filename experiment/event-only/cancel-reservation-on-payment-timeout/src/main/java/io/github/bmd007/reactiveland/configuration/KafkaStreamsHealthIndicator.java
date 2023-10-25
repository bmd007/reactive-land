package io.github.bmd007.reactiveland.configuration;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

import java.util.Set;

import static org.apache.kafka.streams.KafkaStreams.State.*;
import static org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;

@Component
public class KafkaStreamsHealthIndicator implements HealthIndicator {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaStreamsHealthIndicator.class);

    private final String MESSAGE_KEY = "kafka-streams";

    private final StreamsBuilderFactoryBean streams;
    private boolean stillRunning = false;

    public KafkaStreamsHealthIndicator(StreamsBuilderFactoryBean streams) {
        this.streams = streams;
    }

    private synchronized boolean isStillRunning() {
        return stillRunning;
    }

    private synchronized void setStillRunning(boolean stillRunning) {
        this.stillRunning = stillRunning;
    }

    @Override
    public Health health() {
        if (streams.isRunning() && isStillRunning()) {
            return Health.up().withDetail(MESSAGE_KEY, "Available").build();
        }
        return Health.down().withDetail(MESSAGE_KEY, "Not Available").build();
    }

    @PostConstruct
    public void stateAndErrorListener() {
        streams.setStreamsUncaughtExceptionHandler(exception -> {
            LOGGER.error("uncaught error on kafka streams", exception);
            return REPLACE_THREAD;
        });
        streams.setStateListener((newState, oldState) -> {
            LOGGER.info("transit kafka streams state from {} to {}", oldState, newState);
            setStillRunning(Set.of(REBALANCING, RUNNING, CREATED).contains(newState));
        });
    }
}
