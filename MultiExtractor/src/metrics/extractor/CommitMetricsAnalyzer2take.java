package metrics.extractor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

public class CommitMetricsAnalyzer2take {

    private static final String TEMP_REPO_PATH = "./tempRepo";
    private static final String PROJECT_NAME = "kopete";
    public static void main(String[] args) throws Exception {
//    	String projectName = "krita";
        String repoUrl = "https://github.com/kde/kopete.git";
        String branch = "master"; // Branch to analyze
        List<String> commitHashes = new ArrayList<String>();
        try {
        	BufferedReader fileReader = new BufferedReader(new FileReader(new File("kopete.csv")));
        	while(fileReader.ready()) {
        		String line = fileReader.readLine();
        		line = line.strip();
        		commitHashes.add(line);
        	}
        } catch (IOException ioe) {
        	System.out.println("problem reading commit selection file");
        }
//        String[] listOfCommits = {
//        		"113f46fb45fa28907c74ee5776bf92d256e39368",
//        		"d990df0d2874a7da999cebe42909fd4c8fe4a9aa"
//        };
        
        analyzeCommits(repoUrl, branch, commitHashes);
    }

    public static void analyzeCommits(String repoUrl, String branch, List<String> commitHashes) throws Exception {
        File repoDir = new File(TEMP_REPO_PATH);
        if (repoDir.exists()) deleteDirectory(repoDir);

        System.out.println("Cloning repository...");
        Git.cloneRepository().setURI(repoUrl).setBranch(branch).setDirectory(repoDir).call();

        try (Git git = Git.open(repoDir)) {
            Iterable<RevCommit> commits = git.log().call();
            for (RevCommit commit : commits) {
                if (commitHashes.contains(commit.getName())) {
                    System.out.println("Analyzing commit: " + commit.getName());
                    git.checkout().setName(commit.getName()).call();
                    analyzeAllFilesInSystem(git, commit.getName());
                }
            }
        }
        deleteDirectory(repoDir);
    }

    private static void analyzeAllFilesInSystem(Git git, String commitID) throws IOException {
        TreeWalk treeWalk = new TreeWalk(git.getRepository());
        treeWalk.addTree(git.getRepository().resolve("HEAD^{tree}"));
        treeWalk.setRecursive(true);

        Map<String, FileMetrics> fileMetricsMap = new HashMap<>();

        while (treeWalk.next()) {
            String filePath = treeWalk.getPathString();
            if (!isSupportedFileType(filePath)) continue; // Analyze supported file types
            byte[] fileBytes = git.getRepository().open(treeWalk.getObjectId(0)).getBytes();
            String fileContent = new String(fileBytes);

            FileMetrics metrics = calculateMetrics(fileContent);
            fileMetricsMap.put(filePath, metrics);
        }

        saveCommitStatisticsToFile(fileMetricsMap, commitID);
    }

    private static boolean isSupportedFileType(String filePath) {
        return filePath.endsWith(".java") || filePath.endsWith(".py") || filePath.endsWith(".c") || filePath.endsWith(".cpp") || filePath.endsWith(".h") ||
               filePath.endsWith(".cs") || filePath.endsWith(".rb") || filePath.endsWith(".pl") || filePath.endsWith(".js") || filePath.endsWith(".ts");
    }

    private static FileMetrics calculateMetrics(String fileContent) {
        FileMetrics metrics = new FileMetrics();
        String[] lines = fileContent.split("\\n");
        int loc = lines.length;
        int commentLines = (int) Arrays.stream(lines).filter(line -> line.trim().startsWith("//") || line.trim().startsWith("/*") || line.trim().contains("#") || line.trim().startsWith("--")).count();
        int cyclomaticComplexity = calculateCyclomaticComplexity(fileContent);
        HalsteadMetrics halstead = calculateHalsteadMetrics(fileContent);

        metrics.loc = loc;
        metrics.commentRatio = (double) commentLines / loc * 100;
        metrics.cyclomaticComplexity = cyclomaticComplexity;
        metrics.halsteadVolume = halstead.volume;
        metrics.halsteadDifficulty = halstead.difficulty;
        metrics.halsteadEffort = halstead.effort;
        metrics.halsteadBugProp = halstead.bugProp;
        metrics.halsteadTimeRequired = halstead.timeRequired;
        metrics.operandsSum = halstead.operandsSum;
        metrics.operandsUnique = halstead.operandsUnique;
        metrics.operatorsSum = halstead.operatorsSum;
        metrics.operatorsUnique = halstead.operatorsUnique;
        metrics.fanoutExternal = calculateExternalFanout(fileContent);
        metrics.fanoutInternal = calculateInternalFanout(fileContent);
        metrics.faninExternal = calculateExternalFanin(fileContent);
        metrics.faninInternal = calculateInternalFanin(fileContent);
        metrics.maintainabilityIndex = calculateMaintainabilityIndex(metrics);

        return metrics;
    }

    private static int calculateCyclomaticComplexity(String fileContent) {
        int complexity = 1; // Start with 1 for the default path
        complexity += countOccurrences(fileContent, "if\\s*\\(") + countOccurrences(fileContent, "for\\s*\\(") + countOccurrences(fileContent, "while\\s*\\(") + countOccurrences(fileContent, "case ");
        return complexity;
    }

    private static HalsteadMetrics calculateHalsteadMetrics(String fileContent) {
        Set<String> uniqueOperators = new HashSet<>();
        Set<String> uniqueOperands = new HashSet<>();
        int operatorsSum = 0;
        int operandsSum = 0;

        String[] tokens = fileContent.split("\\s+|[{}();,+-/*%]");
        for (String token : tokens) {
            if (isOperator(token)) {
                operatorsSum++;
                uniqueOperators.add(token);
            } else {
                operandsSum++;
                uniqueOperands.add(token);
            }
        }

        double n1 = uniqueOperators.size();
        double n2 = uniqueOperands.size();
        double N1 = operatorsSum;
        double N2 = operandsSum;
        double vocabulary = n1 + n2;
        double length = N1 + N2;
        double volume = length * (Math.log(vocabulary) / Math.log(2));
        double difficulty = (n1 / 2.0) * (N2 / n2);
        double effort = volume * difficulty;
        double bugProp = volume / 3000.0;
        double timeRequired = effort / 18.0;

        return new HalsteadMetrics(operatorsSum, operandsSum, uniqueOperators.size(), uniqueOperands.size(), volume, difficulty, effort, bugProp, timeRequired);
    }

//    private static int calculateExternalFanout(String content) {
//        return (int) Arrays.stream(content.split("\\n")).filter(line -> line.startsWith("import") || line.contains("System.out") || line.contains("require") || line.contains("#include")).count();
//    }
//
//    private static int calculateInternalFanout(String content) {
//        return (int) Arrays.stream(content.split("\\n")).filter(line -> line.contains("this.") || line.contains("new ")).count();
//    }
//
//    private static int calculateExternalFanin(String content) {
//        return (int) Arrays.stream(content.split("\\n")).filter(line -> line.contains("public") && line.contains("(")).count();
//    }
//
//    private static int calculateInternalFanin(String content) {
//        return (int) Arrays.stream(content.split("\\n")).filter(line -> line.contains("private") && line.contains("(")).count();
//    }

    private static double calculateMaintainabilityIndex(FileMetrics metrics) {
        double mi = 171 - 5.2 * Math.log(metrics.halsteadVolume) - 0.23 * metrics.cyclomaticComplexity - 16.2 * Math.log(metrics.loc);
        return Math.min(100, Math.max(0, mi));
    }

    private static int countOccurrences(String content, String keyword) {
        return content.split(keyword).length - 1;
    }

    private static void saveCommitStatisticsToFile(Map<String, FileMetrics> fileMetricsMap, String commitId) throws IOException {
        Map<String, Map<String, Double>> groupedStatistics = new HashMap<>();
        Map<String, List<Double>> metricValues = new HashMap<>();

        fileMetricsMap.values().forEach(metrics -> metrics.toMap().forEach((key, value) -> metricValues.computeIfAbsent(key, k -> new ArrayList<>()).add(value)));

        for (Map.Entry<String, List<Double>> entry : metricValues.entrySet()) {
            List<Double> values = entry.getValue();
            Map<String, Double> stats = new HashMap<>();
            stats.put("mean", calculateMean(values));
            stats.put("max", Collections.max(values));
            stats.put("min", Collections.min(values));
            stats.put("median", calculateMedian(values));
            stats.put("sd", calculateStandardDeviation(values));
            groupedStatistics.put(entry.getKey(), stats);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        String fileName = "commit_metrics_" + commitId + ".json";
        objectMapper.writeValue(Paths.get(fileName).toFile(), groupedStatistics);

        System.out.println("Metrics saved to " + fileName);
    }
    
    private static int calculateExternalFanout(String filePath) {
        // Analyze external calls to other files/methods by looking for include/import statements
        int externalCalls = 0;
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("File does not exist: " + filePath);
            return 0;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("import") || line.contains("#include")) {
                    externalCalls++;
                }
            }
        } catch (IOException e) {
            System.err.println("Error analyzing external fanout for file: " + filePath);
        }
        return externalCalls;
    }

    private static int calculateInternalFanout(String filePath) {
        // Count method calls within the same file
        int internalCalls = 0;
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("File does not exist: " + filePath);
            return 0;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("this.")) {
                    internalCalls++;
                }
            }
        } catch (IOException e) {
            System.err.println("Error analyzing internal fanout for file: " + filePath);
        }
        return internalCalls;
    }

    private static int calculateExternalFanin(String filePath) {
        // Placeholder logic for indexing and counting references from other files to this file
        int faninCount = 0;
        File projectDir = new File("/tempRepo");
        if (projectDir.exists() && projectDir.isDirectory()) {
            File[] files = projectDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.getPath().equals(filePath)) {
                        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                if (line.contains(filePath)) {
                                    faninCount++;
                                }
                            }
                        } catch (IOException e) {
                            System.err.println("Error reading file for external fanin calculation: " + file.getPath());
                        }
                    }
                }
            }
        }
        return faninCount;
    }

    private static int calculateInternalFanin(String filePath) {
        // Count internal method calls to functions defined within the same file
        Set<String> definedMethods = new HashSet<>();
        Set<String> calledMethods = new HashSet<>();
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("File does not exist: " + filePath);
            return 0;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.matches(".*public.*\\(.*\\).*") || line.matches(".*private.*\\(.*\\).*") || line.matches(".*protected.*\\(.*\\).*") || line.matches(".*static.*\\(.*\\).*") || line.matches(".*void.*\\(.*\\).*") || line.matches(".*function.*\\(.*\\).*") ){
                    definedMethods.add(line);
                }
                if (line.contains("(")) {
                    calledMethods.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error analyzing internal fanin for file: " + filePath);
        }
        return (int) calledMethods.stream().filter(definedMethods::contains).count();
    }

    
//    private static void printCommitStatistics(Map<String, FileMetrics> fileMetricsMap, String commitID) {
//    	try {
//    		BufferedWriter bw = new BufferedWriter(new FileWriter(new File("sourceMetrics/" + PROJECT_NAME + "/" + commitID + ".csv")));
//    	
//	        System.out.println("Metrics for each file:");
//	        for (Entry<String, FileMetrics> e: fileMetricsMap.entrySet()) {
//	        	bw.write(e.getKey() + ":" + e.getValue());
//	        }
////	        fileMetricsMap.forEach((path, metrics) -> System.out.println(path + " -> " + metrics));
//	
//	        Map<String, List<Double>> metricValues = new HashMap<>();
//	        fileMetricsMap.values().forEach(metrics -> metrics.toMap().forEach((key, value) -> metricValues.computeIfAbsent(key, k -> new ArrayList<>()).add(value)));
//	        bw.write("\"stats\"" + "{");
//	        System.out.println("Statistics:");
//	        for (Map.Entry<String, List<Double>> entry : metricValues.entrySet()) {
//	            List<Double> values = entry.getValue();
//	            System.out.printf("%s: mean=%.2f, max=%.2f, min=%.2f, median=%.2f, sd=%.2f\n", entry.getKey(),
//	                    calculateMean(values), Collections.max(values), Collections.min(values), calculateMedian(values), calculateStandardDeviation(values));
//	        }
//    	} catch(IOException ioe) {
//    		System.out.println("problem writing to file");
//    	}
//    }

    private static double calculateMean(List<Double> values) {
        return values.stream().mapToDouble(v -> v).average().orElse(0.0);
    }

    private static double calculateMedian(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
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

    private static boolean isOperator(String token) {
        return Arrays.asList("+", "-", "*", "/", "%", "&&", "||", "!", "<", ">", "==", "!=").contains(token);
    }

    private static boolean deleteDirectory(File directory) {
        File[] allContents = directory.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directory.delete();
    }

    static class FileMetrics {
        double commentRatio;
        int loc;
        int cyclomaticComplexity;
        double halsteadVolume;
        double halsteadDifficulty;
        double halsteadEffort;
        double halsteadBugProp;
        double halsteadTimeRequired;
        int operandsSum;
        int operandsUnique;
        int operatorsSum;
        int operatorsUnique;
        int fanoutExternal;
        int fanoutInternal;
        int faninExternal;
        int faninInternal;
        double maintainabilityIndex;

        Map<String, Double> toMap() {
            Map<String, Double> map = new HashMap<>();
            map.put("comment_ratio", commentRatio);
            map.put("cyclomatic_complexity", (double) cyclomaticComplexity);
            map.put("fanout_external", (double) fanoutExternal);
            map.put("fanout_internal", (double) fanoutInternal);
            map.put("fanin_external", (double) faninExternal);
            map.put("fanin_internal", (double) faninInternal);
            map.put("halstead_bugprop", halsteadBugProp);
            map.put("halstead_difficulty", halsteadDifficulty);
            map.put("halstead_effort", halsteadEffort);
            map.put("halstead_timerequired", halsteadTimeRequired);
            map.put("halstead_volume", halsteadVolume);
            map.put("loc", (double) loc);
            map.put("maintainability_index", maintainabilityIndex);
            map.put("operands_sum", (double) operandsSum);
            map.put("operands_unique", (double) operandsUnique);
            map.put("operators_sum", (double) operatorsSum);
            map.put("operators_unique", (double) operatorsUnique);
            return map;
        }

        @Override
        public String toString() {
            return toMap().toString();
        }
    }

    static class HalsteadMetrics {
        int operatorsSum;
        int operandsSum;
        int operatorsUnique;
        int operandsUnique;
        double volume;
        double difficulty;
        double effort;
        double bugProp;
        double timeRequired;

        HalsteadMetrics(int operatorsSum, int operandsSum, int operatorsUnique, int operandsUnique, double volume, double difficulty, double effort, double bugProp, double timeRequired) {
            this.operatorsSum = operatorsSum;
            this.operandsSum = operandsSum;
            this.operatorsUnique = operatorsUnique;
            this.operandsUnique = operandsUnique;
            this.volume = volume;
            this.difficulty = difficulty;
            this.effort = effort;
            this.bugProp = bugProp;
            this.timeRequired = timeRequired;
        }
    }
}