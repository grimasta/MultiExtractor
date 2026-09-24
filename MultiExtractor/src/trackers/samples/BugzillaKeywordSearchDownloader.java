package trackers.samples;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class BugzillaKeywordSearchDownloader {

    private static final String BUGZILLA_API_BASE_URL = "https://bugs.kde.org/rest/bug"; // GNOME Bugzilla API base URL

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

//        System.out.print("Enter keyword for Bugzilla search: ");
//        String keyword;

//        System.out.print("Enter Bug Status (e.g., 'NEW', 'ASSIGNED', 'RESOLVED', 'ALL'): ");
        String bugStatus = "ALL";

//        System.out.print("Enter output JSON file path (e.g., './bug_reports.json'): ");
        String outputFilePath;
        String[] repos = {
        		"plasma-framework",
        		"labplot",
        		"messagelib",
        		"kdepim-addons",
        		"kcontacts"
        };
        
        for (String keyword : repos) {
        	outputFilePath = "Bugzilla/" + keyword +"_keyword_reports.json";
        	
	        try {
	            String bugReportsJson = fetchBugzillaBugReports(keyword, bugStatus);
	            saveBugReportsToJsonFile(bugReportsJson, outputFilePath);
	            System.out.println("Bug reports saved successfully to " + outputFilePath);
	        } catch (Exception e) {
	            System.err.println("Error fetching or saving bug reports: " + e.getMessage());
	        }
        }
    }

    private static String fetchBugzillaBugReports(String keyword, String bugStatus) throws Exception {
        StringBuilder bugzillaBugsEndpoint = new StringBuilder(BUGZILLA_API_BASE_URL + "?keywords=" + keyword);
        if (!bugStatus.equals("ALL")) {
            bugzillaBugsEndpoint.append("&status=").append(encodeUrl(bugStatus));
        }

        URL url = new URL(bugzillaBugsEndpoint.toString());
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

        JSONObject jsonResponse = new JSONObject(response.toString());
        JSONArray bugsArray = jsonResponse.getJSONArray("bugs");

        return bugsArray.toString();
    }

    private static void saveBugReportsToJsonFile(String bugReportsJson, String filePath) throws Exception {
        JSONArray bugsArray = new JSONArray(bugReportsJson);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(bugsArray.toString(4)); // Pretty-print JSON with 4-space indentation
        }
    }

    private static String encodeUrl(String input) throws Exception {
        return java.net.URLEncoder.encode(input, "UTF-8").replace("+", "%20");
    }
}

// To run:
// 1. Compile: javac BugzillaKeywordSearchDownloader.java
// 2. Run: java BugzillaKeywordSearchDownloader

/* Note:
   This program fetches bug reports based on keyword search in Bugzilla GNOME.
   The keyword is searched as a substring in the bug's short description.
*/

