package trackers.samples;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

//    private static final String GITLAB_API_TOKEN = ""; // Your GitLab Access Token
    import org.json.JSONArray;
import org.json.JSONObject;

    public class GitLabCommitsDownloader {
//        private static final String GITHUB_API_TOKEN = System.getenv("GITLAB_API_TOKEN");
        private static final String GITHUB_API_TOKEN = System.getenv("GITLAB_API_TOKEN");
//        private static final String GITHUB_API_TOKEN = System.getenv("GITLAB_API_TOKEN");
        public static void main(String[] args) {

//            System.out.print("Enter GitHub Repository (e.g., 'owner/repo_name'): ");
//            String repository = scanner.nextLine();
//            String repositories = "KDE/epiphany";
            String[] repos = {
            		"KDE/labplot",
            		"KDE/kcontacts",
            		"KDE/kdepim-addons",
            		"KDE/plasma-framework",
            		
            };

//            System.out.print("Enter GitHub API URL (e.g., 'https://api.github.com'): ");
//            String apiUrl = scanner.nextLine();
            String apiUrl = "https://api.github.com";
//            System.out.print("Enter output JSON file path (e.g., './commits.json'): ");
//            String outputFilePath = scanner.nextLine();
            String outputFilePath;
            for (String repository : repos) {
            	outputFilePath = "commits/" + repository.split("/")[1] + ".json";
	            try {
	                JSONArray allCommits = fetchAllGitHubCommits(repository, apiUrl);
	                saveCommitsToJsonFile(allCommits.toString(), outputFilePath);
	                System.out.println("All commits saved successfully to " + outputFilePath);
	            } catch (Exception e) {
	                System.err.println("Error fetching or saving commits: " + e.getMessage());
	            }
            }
        }

        private static JSONArray fetchAllGitHubCommits(String repository, String apiUrl) throws Exception {
            JSONArray allCommits = new JSONArray();
            int page = 1;
            int perPage = 100; // GitHub API max page size for commits is 100.
            boolean morePages = true;

            while (morePages) {
                String commitsEndpoint = apiUrl + "/repos/" + repository + "/commits?page=" + page + "&per_page=" + perPage;
                System.out.println("API URL: " + commitsEndpoint);

                URL url = new URL(commitsEndpoint);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "bearer " + GITHUB_API_TOKEN);
                connection.setRequestProperty("Accept", "application/vnd.github+json");

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

                JSONArray commitsPage = new JSONArray(response.toString());
                for (int i = 0; i < commitsPage.length(); i++) {
                    JSONObject commit = commitsPage.getJSONObject(i);
                    JSONObject simplifiedCommit = new JSONObject();
                    simplifiedCommit.put("id", commit.getString("sha"));
                    simplifiedCommit.put("timestamp", commit.getJSONObject("commit").getJSONObject("author").getString("date"));
                    allCommits.put(simplifiedCommit);
                }

                // Check if more pages are available.
                if (commitsPage.length() < perPage) {
                    morePages = false;
                } else {
                    page++;
                }
            }

            return allCommits;
        }

        private static void saveCommitsToJsonFile(String commitsJson, String filePath) throws Exception {
            JSONArray commitsArray = new JSONArray(commitsJson);

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
                writer.write(commitsArray.toString(4)); // Pretty-print JSON with 4-space indentation
            }
        }

        private static String encodeUrl(String input) throws Exception {
            return java.net.URLEncoder.encode(input, "UTF-8").replace("+", "%20");
        }
    }

    // To run:
    // 1. Replace <YOUR_PERSONAL_ACCESS_TOKEN_HERE> with your GitHub access token.
    // 2. Compile: javac GitHubCommitsDownloader.java
    // 3. Run: java GitHubCommitsDownloader

    /* Note:
       This program fetches all commit SHA IDs and their creation timestamps from the specified GitHub repository.
       Ensure that the access token has `repo` scope for private repositories or `public_repo` for public ones.
    */
