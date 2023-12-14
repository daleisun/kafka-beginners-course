package io.conduktor.demos.kafka;

import java.util.Properties;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProducerDemoKeys {

    private static final Logger LOG = LoggerFactory.getLogger(ProducerDemoKeys.class.getSimpleName());

    public static void main(String[] args) {

        LOG.info("I am a producer!");

        // Create Producer Properties
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "127.0.0.1:9092");
        properties.setProperty("key.serializer", StringSerializer.class.getName());
        properties.setProperty("value.serializer", StringSerializer.class.getName());

        // Create the Producer
        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);

        for (int j = 0; j < 4; j++) {

            for (int i = 0; i < 20; i++) {

                String topic = "demo_java";
                String key = "id_" + i;
                String value = "hello world " + i;

                // Create a Producer Record
                ProducerRecord<String, String> producerRecord = new ProducerRecord<String, String>(topic, key, value);

                // Send data
                producer.send(producerRecord, new Callback() {

                    @Override
                    public void onCompletion(RecordMetadata metadata, Exception exception) {

                        if (exception == null) {
                            LOG.info("Key: " + key + " | Partition: " + metadata.partition());
                        } else {
                            LOG.error("Error while producing", exception);
                        }
                    }

                });

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }

        // Flush and close the producer
        producer.flush();
        producer.close();
    }
}
