package bugzilla.project.info;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class BugzillaProjectReport {

    private static final String BUGZILLA_API_BASE_URL = "https://bugs.kde.org/rest";

    public static void main(String[] args) {
        try {
            Map<String, ProjectStats> projectStats = fetchProjectStats();
            printProjectStats(projectStats);
        } catch (Exception e) {
            System.err.println("Error fetching project stats: " + e.getMessage());
        }
    }

//    private static Map<String, ProjectStats> fetchProjectStats() throws Exception {
//        Map<String, ProjectStats> projectStats = new HashMap<>();
//
//        String productsEndpoint = BUGZILLA_API_BASE_URL + "/product";
//        URL url = new URL(productsEndpoint);
//        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//        connection.setRequestMethod("GET");
//        connection.setRequestProperty("Accept", "application/json");
//
//        int responseCode = connection.getResponseCode();
//        if (responseCode != HttpURLConnection.HTTP_OK) {
//            throw new RuntimeException("Failed : HTTP error code : " + responseCode);
//        }
//
//        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
//        StringBuilder response = new StringBuilder();
//        String line;
//        while ((line = reader.readLine()) != null) {
//            response.append(line);
//        }
//        reader.close();
//        connection.disconnect();
//
//        JSONArray products = new JSONObject(response.toString()).getJSONArray("products");
//        for (int i = 0; i < products.length(); i++) {
//            String productName = products.getJSONObject(i).getString("name");
//            projectStats.put(productName, fetchBugStatsForProduct(productName));
//        }
//
//        return projectStats;
//    }

    private static Map<String, ProjectStats> fetchProjectStats() throws Exception {
        Map<String, ProjectStats> projectStats = new HashMap<>();

        // Include the required parameter "type=accessible" to fetch all accessible products
        String productsEndpoint = BUGZILLA_API_BASE_URL + "/product?type=accessible";
        URL url = new URL(productsEndpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Failed : HTTP error code : " + responseCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        connection.disconnect();

        JSONArray products = new JSONObject(response.toString()).getJSONArray("products");
        for (int i = 0; i < products.length(); i++) {
            String productName = products.getJSONObject(i).getString("name");
            projectStats.put(productName, fetchBugStatsForProduct(productName));
        }

        return projectStats;
    }

    
    
    private static ProjectStats fetchBugStatsForProduct(String productName) throws Exception {
        int totalBugs = 0;
        int resolvedBugs = 0;

        String bugsEndpoint = BUGZILLA_API_BASE_URL + "/bug?product=" + encodeUrl(productName);
        URL url = new URL(bugsEndpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Failed : HTTP error code : " + responseCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        connection.disconnect();

        JSONArray bugs = new JSONObject(response.toString()).getJSONArray("bugs");
        totalBugs = bugs.length();

        for (int i = 0; i < bugs.length(); i++) {
            String status = bugs.getJSONObject(i).getString("status");
            if (status.equalsIgnoreCase("RESOLVED") || status.equalsIgnoreCase("CLOSED")) {
                resolvedBugs++;
            }
        }

        return new ProjectStats(totalBugs, resolvedBugs);
    }

    private static void printProjectStats(Map<String, ProjectStats> projectStats) {
        System.out.println("Project Statistics:");
        for (Map.Entry<String, ProjectStats> entry : projectStats.entrySet()) {
            System.out.println("Project: " + entry.getKey());
            System.out.println("  Total Bugs: " + entry.getValue().totalBugs);
            System.out.println("  Resolved Bugs: " + entry.getValue().resolvedBugs);
        }
    }

    private static String encodeUrl(String input) throws Exception {
        return java.net.URLEncoder.encode(input, "UTF-8").replace("+", "%20");
    }

    private static class ProjectStats {
        int totalBugs;
        int resolvedBugs;

        public ProjectStats(int totalBugs, int resolvedBugs) {
            this.totalBugs = totalBugs;
            this.resolvedBugs = resolvedBugs;
        }
    }
}