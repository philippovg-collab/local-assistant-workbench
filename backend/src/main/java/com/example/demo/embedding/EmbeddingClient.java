package com.example.demo.embedding;

import java.util.List;

public interface EmbeddingClient {

    float[] embed(String input);

    List<float[]> embedAll(List<String> inputs);
}
