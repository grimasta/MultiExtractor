package trackers.samples;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONObject;

public class BugzillaBugReportsFetcher {

    private static final String BUGZILLA_API_BASE_URL = "https://bugs.kde.org/rest/bug"; // GNOME Bugzilla API base URL
    private static final String BUGZILLA_COMMENTS_API_BASE_URL = "https://bugs.kde.org/rest/bug/%s/comment"; // API for comments

    public static void main(String[] args) {
        String[] projectIds = {
//        		"amarok", // Done with comments
//        		"ark", // not Done
//        		"discover", // Done with comments
//        		"dolphin", // Done with comments
//        		"elisa", // Done with comments
//        		"epiphany", 
//        		"evolution", 
//        		"gwenview", // Done with comments
//        		"k3b", // not Done
//        		"kate", // Done with comments  
//        		"kcontacts", 
//        		"kdepim-addons", 
//        		"kdevelop", // Done with comments
//        		"kexi", // Done with comments
//        		"kget", // Done with comments
        		"kmail", // not Done
//        		"kmymoney", // Done with comments
//        		"kolourpaint", // Done with comments
//        		"konqueror",  // Done
        		"konsole",  // not Done
//        		"konversation",  // Done with comments
//        		"ktorrent", // Pending 
//        		"labplot", 
//        		"marble", // Pending
//        		"messagelib", 
//        		"plasma-framework"
//        		"krita", // Pending
//        		"digikam", // Pending
//        		"kopete" // Pending
        };
//        System.out.print("Enter GNOME Bugzilla Product Name (e.g., 'gtk+'): ");
//        String productName = scanner.nextLine();

//        System.out.print("Enter Bug Status (e.g., 'NEW', 'ASSIGNED', 'RESOLVED', 'ALL'): ");
        String bugStatus = "ALL";

//        System.out.print("Enter output JSON file path (e.g., './bug_reports.json'): ");
        for (String projectId : projectIds) {
        	String outputFilePath = "./bugzilla/" + projectId + "-reports.json";

	        try {
//	            String bugReportsJson = fetchBugzillaBugReports(projectId, bugStatus);
	            String bugReportsJson = fetchBugzillaBugReportsWithComments(projectId, bugStatus);
	            saveBugReportsToJsonFile(bugReportsJson, outputFilePath);
	            System.out.println("Bug reports saved successfully to " + outputFilePath);
	        } catch (Exception e) {
	            System.err.println("Error fetching or saving bug reports: " + projectId + e.getMessage());
	        }
        }
    }

	private static String fetchBugzillaBugReportsWithComments(String keyword, String bugStatus) throws Exception {
	    StringBuilder bugzillaBugsEndpoint = new StringBuilder(BUGZILLA_API_BASE_URL + "?short_desc=" + encodeUrl(keyword) + "&short_desc_type=allwordssubstr");
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
	
	    for (int i = 0; i < bugsArray.length(); i++) {
	        JSONObject bug = bugsArray.getJSONObject(i);
	        int bugId = bug.getInt("id");
	        JSONArray comments = fetchCommentsForBug(bugId);
	        bug.put("comments", comments);
	    }
	
	    return bugsArray.toString(4); // Pretty-print JSON with 4-space indentation
	}
	
	private static JSONArray fetchCommentsForBug(int bugId) throws Exception {
	    String commentsEndpoint = String.format(BUGZILLA_COMMENTS_API_BASE_URL, bugId);
	    URL url = new URL(commentsEndpoint);
	    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	    connection.setRequestMethod("GET");
	    connection.setRequestProperty("Accept", "application/json");
	
	    int responseCode = connection.getResponseCode();
	    if (responseCode != HttpURLConnection.HTTP_OK) {
	        throw new RuntimeException("Failed to fetch comments for bug ID " + bugId + ": HTTP error code: " + responseCode);
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
	    return jsonResponse.getJSONObject("bugs").getJSONObject(String.valueOf(bugId)).getJSONArray("comments");
	}
	
	private static void saveBugReportsToJsonFile(String bugReportsJson, String filePath) throws Exception {
	    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
	        writer.write(bugReportsJson);
	    }
	}
	
	private static String encodeUrl(String input) throws Exception {
	    return java.net.URLEncoder.encode(input, "UTF-8").replace("+", "%20");
	}
}

//To run:
//1. Compile: javac BugzillaKeywordSearchDownloader.java
//2. Run: java BugzillaKeywordSearchDownloader

/* Note:
This program fetches bug reports based on keyword search in Bugzilla GNOME and includes comments for each bug.
The comments are added as a "comments" field in each bug JSON object.
*/

    
//    private static String fetchBugzillaBugReportsWithComments(String keyword, String bugStatus) throws Exception {
//        StringBuilder bugzillaBugsEndpoint = new StringBuilder(BUGZILLA_API_BASE_URL + "?short_desc=" + encodeUrl(keyword) + "&short_desc_type=allwordssubstr");
//        if (!bugStatus.equals("ALL")) {
//            bugzillaBugsEndpoint.append("&status=").append(encodeUrl(bugStatus));
//        }
//
//        URL url = new URL(bugzillaBugsEndpoint.toString());
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
//        JSONObject jsonResponse = new JSONObject(response.toString());
//        JSONArray bugsArray = jsonResponse.getJSONArray("bugs");
//
//        for (int i = 0; i < bugsArray.length(); i++) {
//            JSONObject bug = bugsArray.getJSONObject(i);
//            int bugId = bug.getInt("id");
//            JSONArray comments = fetchCommentsForBug(bugId);
//            bug.put("comments", comments);
//        }
//
//        return bugsArray.toString();
//    }
//
//    private static JSONArray fetchCommentsForBug(int bugId) throws Exception {
//        String commentsEndpoint = String.format(BUGZILLA_COMMENTS_API_BASE_URL, bugId);
//        URL url = new URL(commentsEndpoint);
//        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//        connection.setRequestMethod("GET");
//        connection.setRequestProperty("Accept", "application/json");
//
//        int responseCode = connection.getResponseCode();
//        if (responseCode != HttpURLConnection.HTTP_OK) {
//            throw new RuntimeException("Failed to fetch comments for bug ID " + bugId + ": HTTP error code: " + responseCode);
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
//        JSONObject jsonResponse = new JSONObject(response.toString());
//        return jsonResponse.getJSONObject("bugs").getJSONArray(String.valueOf(bugId));
//    }
//
////    private static void saveBugReportsToJsonFile(String bugReportsJson, String filePath) throws Exception {
////        JSONArray bugsArray = new JSONArray(bugReportsJson);
////
////        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
////            writer.write(bugsArray.toString(4)); // Pretty-print JSON with 4-space indentation
////        }
////    }
////
////    private static String encodeUrl(String input) throws Exception {
////        return java.net.URLEncoder.encode(input, "UTF-8").replace("+", "%20");
////    }
//
//    
//    private static String fetchBugzillaBugReports(String productName, String bugStatus) throws Exception {
//        String bugzillaBugsEndpoint = BUGZILLA_API_BASE_URL + "?product=" + encodeUrl(productName);
//        if (!bugStatus.equals("ALL")) {
//            bugzillaBugsEndpoint += "&status=" + encodeUrl(bugStatus);
//        }
//
//        URL url = new URL(bugzillaBugsEndpoint);
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
//        JSONObject jsonResponse = new JSONObject(response.toString());
//        JSONArray bugsArray = jsonResponse.getJSONArray("bugs");
//
//        return bugsArray.toString();
//    }
//
//    private static void saveBugReportsToJsonFile(String bugReportsJson, String filePath) throws Exception {
//        JSONArray bugsArray = new JSONArray(bugReportsJson);
//
//        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
//            writer.write(bugsArray.toString(4)); // Pretty-print JSON with 4-space indentation
//        }
//    }
//
//    private static String encodeUrl(String input) throws Exception {
//        return java.net.URLEncoder.encode(input, "UTF-8").replace("+", "%20");
//    }
//}

// To run:
// 1. Compile: javac BugzillaBugReportsFetcher.java
// 2. Run: java BugzillaBugReportsFetcher

/* Note:
   The Bugzilla REST API for GNOME is publicly accessible, but you can register for a Bugzilla API key if needed.
   Modify the code to include an Authorization header if the API requires authentication.
*/


