package com.example.demo.support;

import java.time.Duration;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

public final class ElasticsearchTestContainerSupport {

    private static final DockerImageName IMAGE = DockerImageName.parse(
        "docker.elastic.co/elasticsearch/elasticsearch:8.13.4"
    );

    private ElasticsearchTestContainerSupport() {
    }

    public static ElasticsearchContainer elasticsearch() {
        return new ElasticsearchContainer(IMAGE)
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("action.destructive_requires_name", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms1g -Xmx1g")
            .waitingFor(Wait.forHttp("/")
                .forPort(9200)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(3)));
    }
}
