package trackers.samples;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONObject;

public class GitlabIssuesDownloader2 {

    private static final String GITLAB_API_TOKEN = System.getenv("GITLAB_API_TOKEN"); // Your GitLab Access Token

    public static void main(String[] args) {
        String[] projectIds = {
//        		"GNOME/gnome-shell", 
//        		"GNOME/gtk", 
//        		"GNOME/libgda", 
//        		"GNOME/mutter", 
//        		"GNOME/nautilus", 
//        		"GNOME/glib", 
//        		"GNOME/balsa", 
//        		"GNOME/gdm",
//        		"GNOME/epiphany",
//        		"GNOME/evolution",
        		"GNOME/gimp"
        		};
//        System.out.print("Enter GitLab Project ID or Path (e.g., 'user/repo_name'): ");
//        String projectId = scanner.nextLine();

//        System.out.print("Enter GitLab API URL (e.g., 'https://gitlab.com/api/v4'): ");
        String apiUrl = "https://gitlab.gnome.org/api/v4";
        
//        System.out.print("Enter output JSON file path (e.g., './issues.json'): ");
        for (String projectId : projectIds) {
	        String outputFilePath = "./" + projectId.split("/")[1] + "_issues.json"; 
	
	        try {
	            JSONArray allIssues = fetchAllGitLabIssuesWithComments(projectId, apiUrl);
	            saveIssuesToJsonFile(allIssues.toString(), outputFilePath);
	            System.out.println("All issues saved successfully to " + outputFilePath);
	        } catch (Exception e) {
	            System.err.println("Error fetching or saving issues: " + e.getMessage());
	        }
	    }
	}

    private static JSONArray fetchAllGitLabIssuesWithComments(String projectId, String apiUrl) throws Exception {
        JSONArray allIssues = new JSONArray();
        int page = 1;
        int perPage = 100; // GitLab API max page size for issues is 100.
        boolean morePages = true;

        while (morePages) {
            String issuesEndpoint = apiUrl + "/projects/" + encodeUrl(projectId) + "/issues?page=" + page + "&per_page=" + perPage;
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

            JSONArray issuesPage = new JSONArray(response.toString());
            for (int i = 0; i < issuesPage.length(); i++) {
                JSONObject issue = issuesPage.getJSONObject(i);
                int issueId = issue.getInt("iid");
                JSONArray comments = fetchCommentsForIssue(projectId, issueId, apiUrl);
                issue.put("comments", comments);
                allIssues.put(issue);
            }

            // Check if more pages are available.
            if (issuesPage.length() < perPage) {
                morePages = false;
            } else {
                page++;
            }
        }

        return allIssues;
    }

    private static JSONArray fetchCommentsForIssue(String projectId, int issueId, String apiUrl) throws Exception {
        String commentsEndpoint = apiUrl + "/projects/" + encodeUrl(projectId) + "/issues/" + issueId + "/notes";
        URL url = new URL(commentsEndpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + GITLAB_API_TOKEN);
        connection.setRequestProperty("Accept", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Failed to fetch comments for issue ID " + issueId + ": HTTP error code: " + responseCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        connection.disconnect();

        return new JSONArray(response.toString());
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

/* Note:
   This program fetches all issues from a GitLab project and includes their comments.
   The comments are added as a "comments" field in each issue JSON object.
*/

    
//    private static JSONArray fetchAllGitLabIssues(String projectId, String apiUrl) throws Exception {
//        JSONArray allIssues = new JSONArray();
//        int page = 1;
//        int perPage = 100; // GitLab API max page size for issues is 100.
//        boolean morePages = true;
//
//        while (morePages) {
//            String issuesEndpoint = apiUrl + "/projects/" + encodeUrl(projectId) + "/issues?page=" + page + "&per_page=" + perPage;
//            URL url = new URL(issuesEndpoint);
//
//            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//            connection.setRequestMethod("GET");
//            connection.setRequestProperty("Authorization", "Bearer " + GITLAB_API_TOKEN);
//            connection.setRequestProperty("Accept", "application/json");
//
//            int responseCode = connection.getResponseCode();
//            if (responseCode != HttpURLConnection.HTTP_OK) {
//                throw new RuntimeException("Failed : HTTP error code : " + responseCode);
//            }
//
//            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
//            StringBuilder response = new StringBuilder();
//            String line;
//            while ((line = reader.readLine()) != null) {
//                response.append(line);
//            }
//            reader.close();
//            connection.disconnect();
//
//            JSONArray issuesPage = new JSONArray(response.toString());
//            allIssues.putAll(issuesPage);
//
//            // Check if more pages are available.
//            if (issuesPage.length() < perPage) {
//                morePages = false;
//            } else {
//                page++;
//            }
//        }
//
//        return allIssues;
//    }
//
//    private static void saveIssuesToJsonFile(String issuesJson, String filePath) throws Exception {
//        JSONArray issuesArray = new JSONArray(issuesJson);
//
//        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
//            writer.write(issuesArray.toString(4)); // Pretty-print JSON with 4-space indentation
//        }
//    }
//
//    private static String encodeUrl(String input) throws Exception {
//        return java.net.URLEncoder.encode(input, "UTF-8").replace("+", "%20");
//    }
//}
//
//// To run:
//// 1. Replace <YOUR_PERSONAL_ACCESS_TOKEN_HERE> with your GitLab access token.
//// 2. Compile: javac GitLabIssuesDownloader.java
//// 3. Run: java GitLabIssuesDownloader
//
///* Note:
//   The program now fetches all issues across multiple pages using pagination and saves them to a single JSON file.
//   Ensure that the access token has the necessary permissions to view all issues, including migrated ones.
//*/
