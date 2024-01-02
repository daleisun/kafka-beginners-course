package io.conduktor.demos.kafka.streams.wikimedia.processor;

import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotCountStreamBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(BotCountStreamBuilder.class);

    private final KStream<String, String> inputStream;

    public BotCountStreamBuilder(KStream<String, String> inputStream) {
        this.inputStream = inputStream;
    }

    public void setup() {
        this.inputStream
                .mapValues(changeJson -> {
                    LOG.info(changeJson);
                    return "bot";
                });
    }
}
