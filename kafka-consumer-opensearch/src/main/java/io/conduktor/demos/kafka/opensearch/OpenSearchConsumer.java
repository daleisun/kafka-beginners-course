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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public static KafkaConsumer<String, String> createKafkaConsumer() {

        String bootstrapServers = "127.0.0.1:9092";
        String groupId = "consumer-opensearch-demo";

        // Create Consumer Properties
        Properties properties = new Properties();

        properties.setProperty("bootstrap.servers", bootstrapServers);
        properties.setProperty("key.deserializer", StringDeserializer.class.getName());
        properties.setProperty("value.deserializer", StringDeserializer.class.getName());
        properties.setProperty("group.id", groupId);
        properties.setProperty("auto.offset.reset", "latest");

        // Create a consumer
        return new KafkaConsumer<>(properties);
    }

    public static void main(String[] args) throws IOException {

        // first create an opensearch client
        RestHighLevelClient openSearchClient = createOpenSearchClient();

        // create our kafka client
        KafkaConsumer<String, String> consumer = createKafkaConsumer();

        // we need to create the index on openSearch if it doesn't exist already
        try (openSearchClient; consumer) {

            GetIndexRequest getIndexRequest = new GetIndexRequest("wikimedia");
            boolean indexExists = openSearchClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);

            if (indexExists) {
                LOG.info("The wikimedia Index already exists!");
            } else {
                CreateIndexRequest createIndexRequest = new CreateIndexRequest("wikimedia");
                openSearchClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
                LOG.info("The Wikimedia Index has been created!");
            }

            // we subscribe the consumer
            consumer.subscribe(Collections.singleton("wikimedia.recentchange"));

            while (true) {

                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(3000));

                int recordCount = records.count();
                LOG.info("Received: " + recordCount + " record(s)");

                for (ConsumerRecord<String, String> record : records) {

                    // send the record into opensearch
                    IndexRequest indexRequest = new IndexRequest("wikimedia").source(record.value(), XContentType.JSON);

                    try {

                        IndexResponse response = openSearchClient.index(indexRequest, RequestOptions.DEFAULT);

                        LOG.info("Inserted 1 document into OpenSearch, response id " + response.getId());
                    } catch (Exception e ) {
                        LOG.error("One message error", e);
                        LOG.error(record.value());
                    }
                }
            }
        }

        // main code logic

        // close things
    }
}
