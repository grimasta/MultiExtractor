package metrics.extractor;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class CommitMetricsAnalyzer4take {
    private static final String TEMP_REPO_DIR = "/tempRepo";
    private static final String BASE_OUTPUT_DIR = "sourceCodeMetrics1";

    public static void main(String[] args) throws Exception {
        String repoUrl = "https://github.com/kde/krita.git";
        String repositoryName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1, repoUrl.lastIndexOf(".git"));
        String branch = "master";
        String repoDirPath = TEMP_REPO_DIR + "/" + repositoryName;
        String outputDirPath = BASE_OUTPUT_DIR + "/" + repositoryName;

        List<String> commitHashes = loadCommitHashes(repositoryName);
        analyzeCommits(repoUrl, branch, repositoryName, commitHashes, repoDirPath, outputDirPath);
    }

    private static List<String> loadCommitHashes(String repositoryName) throws IOException {
        String csvFilePath = repositoryName + ".csv";
        List<String> commitHashes = new ArrayList<>();
        File csvFile = new File(csvFilePath);
        if (!csvFile.exists()) {
            System.err.println("Commit hash file not found: " + csvFilePath);
            return commitHashes;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                commitHashes.add(line.trim());
            }
        }
        return commitHashes;
    }

    public static void analyzeCommits(String repoUrl, String branch, String repositoryName, List<String> commitHashes, String repoDirPath, String outputDirPath) throws Exception {
        File repoDir = new File(repoDirPath);
        if (!repoDir.exists()) {
            System.out.println("Cloning repository...");
            Git.cloneRepository().setURI(repoUrl).setBranch(branch).setDirectory(repoDir).call();
        }

        try (Git git = Git.open(repoDir)) {
            Set<String> analyzedCommits = loadAnalyzedCommits(repositoryName);
            for (RevCommit commit : git.log().call()) {
                if (commitHashes.contains(commit.getName()) && !analyzedCommits.contains(commit.getName())) {
                    String commitId = commit.getName();
                    System.out.println("Analyzing commit: " + commitId);
                    git.checkout().setName(commitId).call();
                    analyzeAllFilesInSystem(git, commitId, repositoryName, outputDirPath);
                    saveExtractionState(repositoryName, commitId);
                }
            }
        }
    }

    private static void analyzeAllFilesInSystem(Git git, String commitId, String repositoryName, String outputDirPath) throws IOException {
        TreeWalk treeWalk = new TreeWalk(git.getRepository());
        treeWalk.addTree(git.getRepository().resolve("HEAD^{tree}"));
        treeWalk.setRecursive(true);

        Map<String, FileMetrics> fileMetricsMap = new LinkedHashMap<>();

        while (treeWalk.next()) {
            String filePath = treeWalk.getPathString();
            if (!isSupportedFileType(filePath)) continue;
            byte[] fileBytes = git.getRepository().open(treeWalk.getObjectId(0)).getBytes();
            String fileContent = new String(fileBytes);

            fileMetricsMap.put(filePath, calculateMetrics(fileContent, filePath));
        }

        saveCommitStatisticsToFile(fileMetricsMap, commitId, outputDirPath);
    }

    private static boolean isSupportedFileType(String filePath) {
        return filePath.endsWith(".java") || filePath.endsWith(".py") || filePath.endsWith(".c") || filePath.endsWith(".cpp") || filePath.endsWith(".h") ||
               filePath.endsWith(".cs");
    }

    private static FileMetrics calculateMetrics(String fileContent, String filePath) {
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
        metrics.fanoutExternal = calculateExternalFanout(filePath, fileContent);
        metrics.fanoutInternal = calculateInternalFanout(fileContent);
        metrics.faninExternal = calculateExternalFanin(filePath);
        metrics.faninInternal = calculateInternalFanin(fileContent);
        metrics.maintainabilityIndex = calculateMaintainabilityIndex(metrics);

        return metrics;
    }

    private static int calculateCyclomaticComplexity(String fileContent) {
        int complexity = 1;
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

    private static int calculateExternalFanout(String filePath, String fileContent) {
        return (int) Arrays.stream(fileContent.split("\\n")).filter(line -> line.contains("import") || line.contains("new ")).count();
    }

    private static int calculateInternalFanout(String fileContent) {
        return (int) Arrays.stream(fileContent.split("\\n")).filter(line -> line.contains("this.")).count();
    }

    private static int calculateExternalFanin(String filePath) {
        return 0;
    }

    private static int calculateInternalFanin(String fileContent) {
        return (int) Arrays.stream(fileContent.split("\\n")).filter(line -> line.contains("public") && line.contains("(")).count();
    }

    private static double calculateMaintainabilityIndex(FileMetrics metrics) {
        double mi = 171 - 5.2 * Math.log(metrics.halsteadVolume) - 0.23 * metrics.cyclomaticComplexity - 16.2 * Math.log(metrics.loc);
        return Math.min(100, Math.max(0, mi));
    }

    private static int countOccurrences(String content, String regex) {
        return content.split(regex).length - 1;
    }

    private static void saveCommitStatisticsToFile(Map<String, FileMetrics> fileMetricsMap, String commitId, String outputDirPath) throws IOException {
	    Map<String, Map<String, Double>> groupedStatistics = new HashMap<>();
	    Map<String, List<Double>> metricValues = new HashMap<>();
	
	    // Collect all metric values per key
	    fileMetricsMap.values().forEach(metrics -> metrics.toMap().forEach((key, value) ->
	        metricValues.computeIfAbsent(key, k -> new ArrayList<>()).add(value)
	    ));
	
	    // Calculate statistics for each metric
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
	
	    // Prepare final JSON structure
	    Map<String, Object> output = new LinkedHashMap<>();
	    output.put("files", fileMetricsMap);
	    output.put("stats", groupedStatistics);
	
	    // Create output directory if not exists
	    File outputDir = new File(outputDirPath);
	    if (!outputDir.exists()) {
	        outputDir.mkdirs();
	    }
	
	    // Write JSON file
	    ObjectMapper objectMapper = new ObjectMapper();
	    String fileName = outputDirPath + "/" + commitId + ".json";
	    objectMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(fileName).toFile(), output);
	
	    System.out.println("Metrics saved to " + fileName);
	}
	
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
	
	private static Set<String> loadAnalyzedCommits(String repositoryName) throws IOException {
	    File stateFile = new File(BASE_OUTPUT_DIR + "/" + repositoryName + "/" + repositoryName + "_analyzed_commits.txt");
	    if (!stateFile.exists()) return new HashSet<>();
	    return new HashSet<>(Files.readAllLines(stateFile.toPath()));
	}
	
	private static void saveExtractionState(String repositoryName, String commitId) throws IOException {
	    File stateFile = new File(BASE_OUTPUT_DIR + "/" + repositoryName + "/" + repositoryName + "_analyzed_commits.txt");
	    try (FileWriter writer = new FileWriter(stateFile, true)) {
	        writer.write(commitId + "\n");
	    }
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
	
	    HalsteadMetrics(int operatorsSum, int operandsSum, int uniqueOperators, int uniqueOperands, double volume, double difficulty, double effort, double bugProp, double timeRequired) {
	        this.operatorsSum = operatorsSum;
	        this.operandsSum = operandsSum;
	        this.operatorsUnique = uniqueOperators;
	        this.operandsUnique = uniqueOperands;
	        this.volume = volume;
	        this.difficulty = difficulty;
	        this.effort = effort;
	        this.bugProp = bugProp;
	        this.timeRequired = timeRequired;
	    }
	}
	
	private static void deleteDirectory(File directoryToBeDeleted) {
	    File[] allContents = directoryToBeDeleted.listFiles();
	    if (allContents != null) {
	        for (File file : allContents) {
	            deleteDirectory(file);
	        }
	    }
	    directoryToBeDeleted.delete();
	}
	
	private static void copyDirectory(Path source, Path target) throws IOException {
	    Files.walk(source).forEach(path -> {
	        try {
	            Files.copy(path, target.resolve(source.relativize(path)));
	        } catch (IOException e) {
	            System.err.println("Error copying " + path + ": " + e.getMessage());
	        }
	    });
	}
}
