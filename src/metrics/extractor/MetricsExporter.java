package metrics.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class MetricsExporter {

    public static void saveCommitStatisticsToFile(Map<String, FileMetrics> fileMetricsMap, String commitId, String projectName) throws IOException {
        Map<String, Map<String, Double>> fileMetricsMapped = new HashMap<>();
        Map<String, Object> resultJson = new HashMap<>();
        Map<String, List<Double>> metricValues = new HashMap<>();

        for (Map.Entry<String, FileMetrics> entry : fileMetricsMap.entrySet()) {
            fileMetricsMapped.put(entry.getKey(), new HashMap<>());
            for (Map.Entry<String, Double> innerEntry : entry.getValue().toMap().entrySet()) {
                fileMetricsMapped.get(entry.getKey()).put(innerEntry.getKey(), innerEntry.getValue());
            }
        }

        fileMetricsMap.values().forEach(metrics ->
            metrics.toMap().forEach((key, value) ->
                metricValues.computeIfAbsent(key, k -> new ArrayList<>()).add(value)
            )
        );

        Map<String, Map<String, Double>> metricStats = new HashMap<>();
        metricStats.put("mean", new HashMap<>());
        metricStats.put("max", new HashMap<>());
        metricStats.put("min", new HashMap<>());
        metricStats.put("median", new HashMap<>());
        metricStats.put("sd", new HashMap<>());

        for (Map.Entry<String, List<Double>> entry : metricValues.entrySet()) {
            List<Double> values = entry.getValue();
            metricStats.get("mean").put(entry.getKey(), calculateMean(values));
            metricStats.get("max").put(entry.getKey(), Collections.max(values));
            metricStats.get("min").put(entry.getKey(), Collections.min(values));
            metricStats.get("median").put(entry.getKey(), calculateMedian(values));
            metricStats.get("sd").put(entry.getKey(), calculateStandardDeviation(values));
        }

        resultJson.put("files", fileMetricsMapped);
        resultJson.put("stats", metricStats);

        ObjectMapper objectMapper = new ObjectMapper();
        String fileName = "sourceCodeMetrics/" + projectName + "/" + commitId + ".json";
        objectMapper.writeValue(Paths.get(fileName).toFile(), resultJson);
        System.out.println("Metrics saved to " + fileName);
    }

    private static double calculateMean(List<Double> values) {
        return values.stream().mapToDouble(v -> v).average().orElse(0.0);
    }

    private static double calculateMedian(List<Double> values) {
        List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
        int size = sorted.size();
        if (size == 0) return 0.0;
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }

    private static double calculateStandardDeviation(List<Double> values) {
        double mean = calculateMean(values);
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0.0);
        return Math.sqrt(variance);
    }
}
