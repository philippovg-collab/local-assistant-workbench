package com.example.demo.support;

import com.example.demo.embedding.EmbeddingClient;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DeterministicEmbeddingClient implements EmbeddingClient {

    private static final int DIMENSIONS = 768;
    private static final Map<String, String> SYNONYMS = buildSynonyms();

    @Override
    public float[] embed(String input) {
        return vectorize(input);
    }

    @Override
    public List<float[]> embedAll(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }

        return inputs.stream().map(this::vectorize).toList();
    }

    private float[] vectorize(String input) {
        String normalized = input == null ? "" : input
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim();
        if (normalized.isEmpty()) {
            float[] fallback = new float[DIMENSIONS];
            fallback[0] = 1.0f;
            return fallback;
        }

        float[] vector = new float[DIMENSIONS];
        for (String rawToken : normalized.split("\\s+")) {
            if (rawToken.length() < 2) {
                continue;
            }
            String token = SYNONYMS.getOrDefault(rawToken, rawToken);
            int bucket = Math.floorMod(token.hashCode(), DIMENSIONS);
            vector[bucket] += 1.0f;
        }

        double magnitude = 0.0d;
        for (float value : vector) {
            magnitude += value * value;
        }

        if (magnitude == 0.0d) {
            vector[0] = 1.0f;
            return vector;
        }

        float scale = (float) (1.0d / Math.sqrt(magnitude));
        for (int index = 0; index < vector.length; index++) {
            vector[index] *= scale;
        }

        return vector;
    }

    private static Map<String, String> buildSynonyms() {
        Map<String, String> synonyms = new HashMap<>();
        synonyms.put("цена", "price");
        synonyms.put("стоит", "price");
        synonyms.put("стоимость", "price");
        synonyms.put("сколько", "price");
        synonyms.put("тариф", "plan");
        synonyms.put("план", "plan");
        synonyms.put("premium", "premium");
        synonyms.put("премиум", "premium");
        synonyms.put("премиальный", "premium");
        synonyms.put("поддержка", "support");
        synonyms.put("support", "support");
        synonyms.put("приоритетная", "priority");
        synonyms.put("priority", "priority");
        return Map.copyOf(synonyms);
    }
}
