package io.conduktor.demos.kafka.opensearch;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class OpenSearchConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchConsumer.class.getSimpleName());

    public static RestHighLevelClient createOpenSearchClient() {

        // String connectString = "http://localhost:9200";
        String connectString = "https://evgcv88vjl:enr5uofx84@myopensearch-5551407621.us-east-1.bonsaisearch.net:443";

        // we build a URI from the connection string
        RestHighLevelClient restHighLevelClient;
        URI connectUri = URI.create(connectString);

        // extract login information if it exists
        String userInfo = connectUri.getUserInfo();

        if (userInfo == null) {

            // REST client without security
            restHighLevelClient = new RestHighLevelClient(RestClient.builder(
                    new HttpHost(connectUri.getHost(), connectUri.getPort(), "http")));
        } else {

            // REST client with security
            String[] auth = userInfo.split(":");

            CredentialsProvider cp = new BasicCredentialsProvider();
            cp.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(auth[0], auth[1]));

            restHighLevelClient = new RestHighLevelClient(
                    RestClient.builder(new HttpHost(connectUri.getHost(), connectUri.getPort(), connectUri.getScheme()))
                            .setHttpClientConfigCallback(
                                    httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(cp)
                                            .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())));
        }

        return restHighLevelClient;
    }

    private static KafkaConsumer<String, String> createKafkaConsumer() {

        String bootstrapServers = "127.0.0.1:9092";
        String groupId = "consumer-opensearch-demo";

        // Create Consumer Properties
        Properties properties = new Properties();

        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        // Create a consumer
        return new KafkaConsumer<>(properties);
    }

    private static String extractId(String json) {

        JsonObject record = JsonParser.parseString(json).getAsJsonObject();
        JsonObject recordMeta = record.get("meta").getAsJsonObject();
        return recordMeta.get("id").getAsString();
    }

    public static void main(String[] args) throws IOException {

        // first create an opensearch client
        RestHighLevelClient openSearchClient = createOpenSearchClient();

        // create our kafka client
        KafkaConsumer<String, String> consumer = createKafkaConsumer();

        // Get a reference to the main thread
        final Thread mainThread = Thread.currentThread();

        // Add the shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                LOG.info("Detected a shutdown, let's exit by calling consumer.wakeup()...");
                consumer.wakeup();

                // Join the main thread to allow the execution of the code in the main thread
                try {
                    mainThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        // we need to create the index on openSearch if it doesn't exist already
        try (openSearchClient; consumer) {

            GetIndexRequest getIndexRequest = new GetIndexRequest("wikimedia_new");
            boolean indexExists = openSearchClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);

            if (indexExists) {
                LOG.info("The wikimedia Index already exists!");
            } else {
                CreateIndexRequest createIndexRequest = new CreateIndexRequest("wikimedia_new");
                openSearchClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
                LOG.info("The Wikimedia Index has been created!");
            }

            // we subscribe the consumer
            consumer.subscribe(Collections.singleton("wikimedia.recentchange"));

            while (true) {

                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(3000));

                int recordCount = records.count();
                LOG.info("Received: " + recordCount + " record(s)");

                BulkRequest bulkRequest = new BulkRequest();

                for (ConsumerRecord<String, String> record : records) {

                    // send the record into opensearch

                    // strategy 1
                    // define an ID using Kafka Record coordinates
                    // String id = record.topic() + "_" + record.partition() + "_" + record.offset();

                    // strategy 2
                    // extract id from record itself
                    String id = extractId(record.value());
                    IndexRequest indexRequest = new IndexRequest("wikimedia_new")
                            .source(record.value(), XContentType.JSON)
                            .id(id);

                    try {

                        bulkRequest.add(indexRequest);
                    } catch (Exception e) {

                    }
                }

                if (bulkRequest.numberOfActions() > 0) {
                    BulkResponse bulkResponse = openSearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);
                    LOG.info("Inserted " + bulkResponse.getItems().length + " record(s)");

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // commit offsets after the batch is consumed
                    consumer.commitSync();
                    LOG.info("Offsets have been committed");
                }

            }
        } catch (WakeupException e) {
            LOG.info("Consumer is starting to shutdown");
        } catch (Exception e) {
            LOG.error("Unexpected exception in the consumer", e);
        } finally {
            consumer.close();  // close the consumer, this will also commit offsets
            openSearchClient.close();
            LOG.info("The consumer is now gracefully shut down");
        }
    }
}
