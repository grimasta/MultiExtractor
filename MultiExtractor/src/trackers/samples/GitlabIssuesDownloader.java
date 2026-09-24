package trackers.samples;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import org.json.JSONArray;

public class GitlabIssuesDownloader {

    private static final String GITLAB_API_TOKEN = System.getenv("GITLAB_API_TOKEN");// Your GitLab Access Token

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String[] projectIds = {"GNOME/gnome-shell", "GNOME/gtk", "GNOME/libgda", "GNOME/mutter", "GNOME/nautilus", "GNOME/glib", "GNOME/balsa", "GNOME/gdm", "GNOME/gimp"};
//        String[] projectIds = {"labplot", "messagelib", "plasma-framework", "kcontacts", 
//        System.out.print("Enter GitLab Project ID or Path (e.g., 'user/repo_name'): ");
//        String projectId = scanner.nextLine();
//        projectId = "GNOME/libgda";
//        System.out.print("Enter GitLab API URL (e.g., 'https://gitlab.com/api/v4'): ");
//        String apiUrl = scanner.nextLine();
        String apiUrl = "https://gitlab.gnome.org/api/v4";

//        System.out.print("Enter output JSON file path (e.g., './issues.json'): ");
//        String outputFilePath = scanner.nextLine();
        for(String projectId : projectIds) {
        	String outputFilePath = "./" + projectId.split("/")[1] + "_issues.json";
        	try {
                String issuesJson = fetchGitLabIssues(projectId, apiUrl);
                saveIssuesToJsonFile(issuesJson, outputFilePath);
                System.out.println("Issues saved successfully to " + outputFilePath);
            } catch (Exception e) {
                System.err.println("Error fetching or saving issues: " + e.getMessage());
            }
        }
        
        
        
    }

    private static String fetchGitLabIssues(String projectId, String apiUrl) throws Exception {
        String issuesEndpoint = apiUrl + "/projects/" + encodeUrl(projectId) + "/issues";
        URL url = new URL(issuesEndpoint);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + GITLAB_API_TOKEN);
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

        return response.toString();
    }

    private static void saveIssuesToJsonFile(String issuesJson, String filePath) throws Exception {
        JSONArray issuesArray = new JSONArray(issuesJson);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(issuesArray.toString(4)); // Pretty-print JSON with 4-space indentation
        }
    }

    private static String encodeUrl(String input) throws Exception {
        return java.net.URLEncoder.encode(input, "UTF-8").replace("+", "%20");
    }
}

// To run:
// 1. Replace <YOUR_PERSONAL_ACCESS_TOKEN_HERE> with your GitLab access token.
// 2. Compile: javac GitLabIssuesDownloader.java
// 3. Run: java GitLabIssuesDownloader
