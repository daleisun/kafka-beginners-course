package io.conduktor.demos.kafka.wikimedia;

import com.launchdarkly.eventsource.ConnectStrategy;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.background.BackgroundEventHandler;
import com.launchdarkly.eventsource.background.BackgroundEventSource;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import java.net.URI;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class WikimediaChangesProducer {
    
    public static void main(String[] args) {
    	
    	String bootstrapServers = "127.0.0.1:9092";
    	
    	// Create producer properties
    	Properties properties = new Properties();
    	
    	properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    	properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    	properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    	
		// Set safe producer config (kafka <= 2.8)
		properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
		properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
		properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
		
    	// Create the producer
    	KafkaProducer<String, String> producer = new KafkaProducer<>(properties);
    	
    	String topic = "wikimedia.recentchange";
    	
		BackgroundEventHandler eventHandler = new WikimediaChangeHandler(producer, topic);
    	String url = "https://stream.wikimedia.org/v2/stream/recentchange";
		EventSource.Builder eventSourceBuilder = new EventSource.Builder(
				ConnectStrategy.http(URI.create(url))
						.connectTimeout(10, TimeUnit.SECONDS)
		);
		BackgroundEventSource.Builder builder = new BackgroundEventSource.Builder(eventHandler, eventSourceBuilder);
		BackgroundEventSource eventSource = builder.build();

		// Start the producer in another thread
    	eventSource.start();

		// Produce for 10 minutes and block the program until then
		try {
			TimeUnit.MINUTES.sleep(10);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }
}
