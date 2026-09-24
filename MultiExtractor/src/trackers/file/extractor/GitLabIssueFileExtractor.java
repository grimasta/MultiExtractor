package trackers.file.extractor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

public class GitLabIssueFileExtractor {

    public static void main(String[] args) {
        String[] projectIds = {
        		"gnome-shell", 
        		"gtk", 
        		"libgda", 
        		"mutter", 
        		"nautilus", 
        		"glib", 
        		"balsa", 
        		"gdm",
        		"epiphany",
        		"evolution",
        		"gimp"
        		};
        String inputPath = "gitlab_with_comments\\";
//        String inputFilePath = "C:\\Users\\spawn\\eclipse_space\\eclipse-java-workspace\\IssueTrackerExtractor2\\gitlab_with_comments\\";
        String outputPath = "FilesWithDates\\";
        for (String projectID : projectIds) {
        	
	        String inputFilePath = inputPath + projectID + "_issues.json";
	//        System.out.print("Enter the path to the input JSON file with issues and comments: ");
	//        String inputFilePath = scanner.nextLine();
	
	//        System.out.print("Enter output JSON file path (e.g., './file_mentions.json'): ");
	//        String outputFilePath = scanner.nextLine();

	        String outputFilePath = outputPath + projectID + ".json";
	        File ofp = new File(outputPath);
	        if (!ofp.isDirectory())
	        	ofp.mkdirs();
	        try {
	            JSONArray inputJson = readFromJsonFile(inputFilePath);
	            JSONObject report = generateFileMentionsReport(inputJson);
	            saveToJsonFile(report, outputFilePath);
	            System.out.println("File mentions saved successfully to " + outputFilePath);
	        } catch (Exception e) {
	            System.err.println("Error processing file mentions: " + e.getMessage());
	        }
        }
    }
        

    private static JSONObject generateFileMentionsReport(JSONArray bugsArray) {
        JSONObject report = new JSONObject();

        Pattern filePattern = Pattern.compile("(?:\\.\\./)*[a-zA-Z0-9._/-]+\\.[a-zA-Z0-9]{1,5}");
//        Pattern filePattern = Pattern.compile("\\+\\d+ \\-\\d+\\s+(?:\\.\\./)*[a-zA-Z0-9._/-]+\\.[a-zA-Z0-9]{1,5}");
        HashMap<String, Set<String>> dateToFileMap = new HashMap<>();

        for (int i = 0; i < bugsArray.length(); i++) {
            JSONObject bug = bugsArray.getJSONObject(i);
            String description = bug.optString("description", "");
            String createdAt = bug.optString("created_at", "unknown_date");

            // Initialize the file set for the creation date if not already present
            dateToFileMap.putIfAbsent(createdAt, new HashSet<>());

            // Check mentions in description
            Matcher descriptionMatcher = filePattern.matcher(description);
            while (descriptionMatcher.find()) {
                String filename = descriptionMatcher.group();
                dateToFileMap.get(createdAt).add(filename);
//                System.out.println("[Description] Date: " + createdAt + " | Filename: " + filename);
            }

            // Check mentions in comments
            JSONArray comments = bug.optJSONArray("comments");
            if (comments != null) {
                for (int j = 0; j < comments.length(); j++) {
                    JSONObject comment = comments.getJSONObject(j);
                    String body = comment.optString("text", "");
                    Matcher commentMatcher = filePattern.matcher(body);
                    while (commentMatcher.find()) {
                        String filename = commentMatcher.group();
                        dateToFileMap.get(createdAt).add(filename);
//                        System.out.println("[Comment] Date: " + createdAt + " | Filename: " + filename);
                    }
                }
            }
        }

        for (String date : dateToFileMap.keySet()) {
            report.put(date, dateToFileMap.get(date));
        }

        return report;
    }

    private static JSONArray readFromJsonFile(String filePath) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            return new JSONArray(jsonContent.toString());
        }
    }

    private static void saveToJsonFile(JSONObject json, String filePath) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(json.toString(4)); // Pretty-print JSON with 4-space indentation
        }
    }
}