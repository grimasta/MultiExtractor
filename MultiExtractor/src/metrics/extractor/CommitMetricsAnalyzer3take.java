package metrics.extractor;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.api.Git;
//import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CommitMetricsAnalyzer3take {
	private static final String PROJECT_NAME = "sourceCodeMetrics/";
    private static final String TEMP_REPO_PATH = "tempRepo/";
    
//    public static void main(String[] args) throws Exception {
//    	File commit_folder = new File("selected_commits");
//    	for(String filename : commit_folder.list()) {
//    		String projectname = filename.replace(".csv","");
//    	
//	    	File project_name = new File(PROJECT_NAME + projectname + "/");
//	    	if (!project_name.isDirectory())
//	    		project_name.mkdirs();
//	//        String repoUrl = "https://github.com/KDE/krita.git"	;
//	        String repoUrl = "https://github.com/apache/" + projectname + ".git";
//	        String branch = "trunk"; // Branch to analyze
//	        List<String> commitHashes = new ArrayList<String>(); 
//	        try {
//	        	BufferedReader fileReader = new BufferedReader(new FileReader(new File("selected_commits/" + projectname + ".csv")));
//	        	while(fileReader.ready()) {
//	        		String line = fileReader.readLine();
//	        		line = line.strip();
//	        		commitHashes.add(line);
//	        	}
//	        } catch (IOException ioe) {
//	        	System.out.println("problem reading commit selection file");
//	        }
//	
//	        analyzeCommits(repoUrl, branch, commitHashes);
//    	}
//    }
    
    public static void main(String[] args) throws Exception {
        File commit_folder = new File("selected_commits/requires_authentication");
        for (String filename : commit_folder.list()) {
        	if(new File("selected_commits/requires_authentication/" + filename).isDirectory())
        		continue;
        	String projectname = filename.replace(".csv", "");
            File projectDir = new File(PROJECT_NAME + projectname + "/");
            if (!projectDir.isDirectory())
                projectDir.mkdirs();
            List<String> doneHashes = new ArrayList<>();
            File DoneCommitFolder = new File(PROJECT_NAME + projectname + "/");
            for (String commitHashDone : DoneCommitFolder.list()) {
            	doneHashes.add(commitHashDone.replace(".json", ""));
            }            	
            String repoUrl = "https://github.com/apache/" + projectname + ".git";
            List<String> commitHashes = new ArrayList<>();

            try (BufferedReader fileReader = new BufferedReader(new FileReader("selected_commits/requires_authentication/" + projectname + ".csv"))) {
                while (fileReader.ready()) {
                	String commitHash = fileReader.readLine().strip();
                	if (!doneHashes.contains(commitHash))
                		commitHashes.add(commitHash);
                }
            } catch (IOException ioe) {
                System.out.println("Problem reading commit selection file for " + projectname);
                continue;
            }

            // Clone repo (bare or full)
            
            File localPath = new File("tempRepo/" + projectname + "/");
            deleteDirectory(localPath);
            Git git;
//            = Git.cloneRepository()
//                .setURI(repoUrl)
//                .setDirectory(localPath)
//                .setCloneAllBranches(true)
//                .call();
            
            try {
                git = Git.cloneRepository()
	                    .setURI(repoUrl)
	                    .setDirectory(localPath)
	                    .setCloneAllBranches(true)
	                    .call();
            } catch (TransportException authError) {
                if (authError.getMessage().contains("not authorized")) {
                    UsernamePasswordCredentialsProvider credentials = promptForCredentials(repoUrl);

                    git = Git.cloneRepository()
	                        .setURI(repoUrl)
	                        .setDirectory(localPath)
	                        .setCloneAllBranches(true)
	                        .setCredentialsProvider(credentials)
	                        .call();
                } else {
                    throw authError;
                }
            }

            

            // List all remote branches
            List<Ref> branches = git.branchList().setListMode(ListMode.REMOTE).call();
        	List<String> done_branches = new ArrayList<>();
        	done_branches.add("asf-site");
        	done_branches.add("branch-3.0");
        	done_branches.add("branch-3.1");
        	done_branches.add("branch-3.2");
            for (Ref branchRef : branches) {
                String fullBranchName = branchRef.getName(); // e.g., refs/remotes/origin/main
                String shortBranchName = fullBranchName.replace("refs/remotes/origin/", "");
                if (done_branches.contains(shortBranchName)) {
                	System.out.println("Branch " + shortBranchName + " was skipped by the user");
                	continue;
                }

                System.out.println("Analyzing branch: " + shortBranchName);

                if (commitHashes.size()==0) {
                	System.out.println("No Commits left to analyze in this repo " + projectname);
            		continue;
                }
                // Checkout branch
                boolean branchExists = git.getRepository().getRefDatabase().findRef(shortBranchName) != null;
                
                // Clean before checkout
                git.reset().setMode(ResetCommand.ResetType.HARD).call();
                git.clean().setCleanDirectories(true).setForce(true).call();
                
                git.checkout()
                    .setName(shortBranchName)
                    .setCreateBranch(!branchExists)
                    .setStartPoint(fullBranchName)
                    .setForced(true)
                    .call();
//                git.checkout()
//                    .setName(shortBranchName)
//                    .setCreateBranch(true)
//                    .setStartPoint(fullBranchName)
//                    .call();

                // Analyze matching commits
                analyzeCommits(repoUrl, shortBranchName, commitHashes, projectname);
            }

            git.close();
        }
    }


    public static void analyzeCommits(String repoUrl, String branch, List<String> commitHashes, String projectName) throws Exception {
        // ✅ Option 3: Create a unique temporary directory for the clone
        String uniqueRepoDirName = projectName + "_" + System.currentTimeMillis();
        File repoDir = new File(TEMP_REPO_PATH + uniqueRepoDirName + "/");

        // ✅ Option 1: Robust directory cleanup if anything remains (safety check)
        if (repoDir.exists()) {
            deleteDirectory(repoDir);
        }

        System.out.println("Cloning repository into " + repoDir.getAbsolutePath() + "...");
        Git.cloneRepository()
            .setURI(repoUrl)
            .setBranch(branch)
            .setDirectory(repoDir)
            .call();

        try (Git git = Git.open(repoDir)) {
            Iterable<RevCommit> commits = git.log().call();

            for (RevCommit commit : commits) {
                if (commitHashes.contains(commit.getName())) {
                    final String commitId = commit.getName();
                    try {
                        System.out.println("Analyzing commit: " + commitId);
                        
                        // Clean before checkout
                        git.reset().setMode(ResetCommand.ResetType.HARD).call();
                        git.clean().setCleanDirectories(true).setForce(true).call();
                        
                        git.checkout().setName(commitId).setForced(true).call();
                        analyzeAllFilesInSystem(git, commitId, projectName);
                        commitHashes.remove(commit.getName());
                    } catch (Exception e) {
                        System.err.println("Error analyzing commit " + commitId + ": " + e.getMessage());
                    }
                }
            }

        } finally {
            // Clean up temp directory after analysis
            deleteDirectory(repoDir);
        }
    }
    
//    public static void analyzeCommits(String repoUrl, String branch, List<String> commitHashes, String projectname) throws Exception {
//        File repoDir = new File(TEMP_REPO_PATH + projectname + "/");
//        try {
//        	if (repoDir.exists()) deleteDirectory(repoDir);
//        } catch(IOException ioe) {
//        	System.out.println(ioe.getMessage());
//        }
//
//        System.out.println("Cloning repository...");
//        Git.cloneRepository().setURI(repoUrl).setBranch(branch).setDirectory(repoDir).call();
//
//        try (Git git = Git.open(repoDir)) {
//            Iterable<RevCommit> commits = git.log().call();
////            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
////            List<Future<?>> futures = new ArrayList<>();
//
//            for (RevCommit commit : commits) {
//                if (commitHashes.contains(commit.getName())) {
//                    final String commitId = commit.getName();
////                    futures.add(executor.submit(() -> {
//                        try {
//                            System.out.println("Analyzing commit: " + commitId);
//                            git.checkout().setName(commitId).call();
//                            analyzeAllFilesInSystem(git, commitId);
//                        } catch (Exception e) {
//                            System.err.println("Error analyzing commit " + commitId + ": " + e.getMessage());
//                        }
////                    }));
//                }
//            }
//
////            for (Future<?> future : futures) {
////                future.get();
////            }
////            executor.shutdown();
////            while(!executor.isTerminated());
//        }
//        deleteDirectory(repoDir);
//    }
    
    
    

    private static void analyzeAllFilesInSystem(Git git, String commitId, String projectName) throws IOException {
        TreeWalk treeWalk = new TreeWalk(git.getRepository());
        treeWalk.addTree(git.getRepository().resolve("HEAD^{tree}"));
        treeWalk.setRecursive(true);

        Map<String, FileMetrics> fileMetricsMap = new ConcurrentHashMap<>();

        while (treeWalk.next()) {
            String filePath = treeWalk.getPathString();
            if (!isSupportedFileType(filePath)) continue; // Analyze supported file types
            byte[] fileBytes = git.getRepository().open(treeWalk.getObjectId(0)).getBytes();
            String fileContent = new String(fileBytes);
            
            fileMetricsMap.put(filePath, calculateMetrics(fileContent, filePath));
        }

        saveCommitStatisticsToFile(fileMetricsMap, commitId, projectName);
    }

    private static boolean isSupportedFileType(String filePath) {
        return filePath.endsWith(".java") || filePath.endsWith(".py") || filePath.endsWith(".c") || filePath.endsWith(".cpp") || filePath.endsWith(".h") ||
               filePath.endsWith(".cs") || filePath.endsWith(".rb") || filePath.endsWith(".pl") || filePath.endsWith(".js") || filePath.endsWith(".ts");
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
        metrics.fanoutExternal = calculateExternalFanout(fileContent);
        metrics.fanoutInternal = calculateInternalFanout(fileContent);
        metrics.faninExternal = calculateExternalFanin(fileContent);
        metrics.faninInternal = calculateInternalFanin(fileContent);
        metrics.maintainabilityIndex = calculateMaintainabilityIndex(metrics);

        return metrics;
    }

    private static int calculateExternalFanout(String content) {
        return (int) Arrays.stream(content.split("\\n")).filter(line -> line.startsWith("import") || line.contains("System.out") || line.contains("require") || line.contains("#include")).count();
    }

    private static int calculateInternalFanout(String content) {
        return (int) Arrays.stream(content.split("\\n")).filter(line -> line.contains("this.") || line.contains("new ")).count();
    }

    private static int calculateExternalFanin(String content) {
        return (int) Arrays.stream(content.split("\\n")).filter(line -> line.contains("public") && line.contains("(")).count();
    }

    private static int calculateInternalFanin(String content) {
        return (int) Arrays.stream(content.split("\\n")).filter(line -> line.contains("private") && line.contains("(")).count();
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

//    private static int calculateExternalFanout(String filePath) {
//        // Use parsing libraries to analyze file dependencies and count references to external files
//        Set<String> externalCalls = new HashSet<>();
//        File file = new File(filePath);
//        if (!file.exists()) {
//            System.err.println("File does not exist for external fanout calculation: " + filePath);
//            return 0;
//        }
//        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
//            String line;
//            while ((line = br.readLine()) != null) {
//                if (line.contains("import") || line.contains("include")) {
//                    externalCalls.add(line.trim());
//                }
//            }
//        } catch (IOException e) {
//            System.err.println("Error reading file for external fanout calculation: " + filePath);
//        }
//        return externalCalls.size();
//    }
//
//    private static int calculateInternalFanout(String filePath) {
//        // Count method calls within the same file
//        Set<String> internalCalls = new HashSet<>();
//        File file = new File(filePath);
//        if (!file.exists()) {
////            System.err.println("File does not exist for internal fanout calculation: " + filePath);
//            return 0;
//        }
//        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
//            String line;
//            while ((line = br.readLine()) != null) {
//                if (line.contains("this.")) {
//                    internalCalls.add(line.trim());
//                }
//            }
//        } catch (IOException e) {
//            System.err.println("Error reading file for internal fanout calculation: " + filePath);
//        }
//        return internalCalls.size();
//    }
//
//    private static int calculateExternalFanin(String filePath) {
//        // This method requires indexing all functions and analyzing cross-references in the project
//        int faninCount = 0;
//        File projectDir = new File("/tempRepo");
//        if (projectDir.exists() && projectDir.isDirectory()) {
//            File[] files = projectDir.listFiles();
//            if (files != null) {
//                for (File file : files) {
//                    if (file.exists() && !file.getPath().equals(filePath)) {
//                        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
//                            String line;
//                            while ((line = br.readLine()) != null) {
//                                if (line.contains(filePath)) {
//                                    faninCount++;
//                                }
//                            }
//                        } catch (IOException e) {
//                            System.err.println("Error reading file for external fanin calculation: " + file.getPath());
//                        }
//                    }
//                }
//            }
//        }
//        return faninCount;
//    }
//
//    private static int calculateInternalFanin(String filePath) {
//        // Count calls to functions defined within the same file
//        Set<String> definedMethods = new HashSet<>();
//        Set<String> calledMethods = new HashSet<>();
//        File file = new File(filePath);
//        if (!file.exists()) {
////            System.err.println("File does not exist for internal fanin calculation: " + filePath);
//            return 0;
//        }
//        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
//            String line;
//            while ((line = br.readLine()) != null) {
//                if (line.matches(".*public.*\\(.*\\).*") || line.matches(".*private.*\\(.*\\).*") || line.matches(".*protected.*\\(.*\\).*") || line.matches(".*static.*\\(.*\\).*") || line.matches(".*void.*\\(.*\\).*") || line.matches(".*function.*\\(.*\\).*")) {
//                    definedMethods.add(line);
//                }
//                if (line.contains("(")) {
//                    calledMethods.add(line);
//                }
//            }
//        } catch (IOException e) {
//            System.err.println("Error reading file for internal fanin calculation: " + filePath);
//        }
//        // Return the number of methods that are called internally
//        return (int) calledMethods.stream().filter(definedMethods::contains).count();
//    }

    private static double calculateMaintainabilityIndex(FileMetrics metrics) {
        double mi = 171 - 5.2 * Math.log(metrics.halsteadVolume) - 0.23 * metrics.cyclomaticComplexity - 16.2 * Math.log(metrics.loc);
        return Math.min(100, Math.max(0, mi));
    }

    private static int countOccurrences(String content, String regex) {
        return content.split(regex).length - 1;
    }

    private static void saveCommitStatisticsToFile(Map<String, FileMetrics> fileMetricsMap, String commitId, String projectName) throws IOException {
    	Map<String, Map<String, Double>> fileMetricsMapped = new HashMap<>();
    	Map<String, Object> resultJson = new HashMap<>();
    	Map<String, List<Double>> metricValues = new HashMap<>();
    	
    	for(Map.Entry<String, FileMetrics> entry: fileMetricsMap.entrySet()) {
    		fileMetricsMapped.put(entry.getKey(), new HashMap<>());
    		for (Map.Entry<String, Double> innerEntry : entry.getValue().toMap().entrySet())
    			fileMetricsMapped.get(entry.getKey()).put(innerEntry.getKey(), innerEntry.getValue());
    	}
    	
    	fileMetricsMap.values().forEach(metrics -> 
    	    metrics.toMap().forEach((key, value) -> 
    	        metricValues.computeIfAbsent(key, k -> new ArrayList<>()).add(value)
    	    )
    	);
    	// Compute statistics for each metric
    	Map<String, Map<String, Double>> metricStats = new HashMap<>();
    	metricStats.put("mean", new HashMap<>());
    	metricStats.put("max", new HashMap<>());
    	metricStats.put("min", new HashMap<>());
    	metricStats.put("median", new HashMap<>());
    	metricStats.put("sd", new HashMap<>());
    	
    	for (Map.Entry<String, List<Double>> entry : metricValues.entrySet()) {
    	    metricStats.get("mean").put(entry.getKey(), calculateMean(entry.getValue()));
    	    metricStats.get("max").put(entry.getKey(), Collections.max(entry.getValue()));
    	    metricStats.get("min").put(entry.getKey(), Collections.min(entry.getValue()));
    	    metricStats.get("median").put(entry.getKey(), calculateMedian(entry.getValue()));
    	    metricStats.get("sd").put(entry.getKey(), calculateStandardDeviation(entry.getValue()));
    	}

    	// Construct final JSON structure
    	resultJson.put("files", fileMetricsMapped);
    	resultJson.put("stats", metricStats);

    	ObjectMapper objectMapper = new ObjectMapper();
    	String fileName = PROJECT_NAME + projectName + "/" + commitId + ".json";
    	objectMapper.writeValue(Paths.get(fileName).toFile(), resultJson);
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

//    private static boolean deleteDirectory(File directory) {
//        File[] allContents = directory.listFiles();
//        if (allContents != null) {
//            for (File file : allContents) {
//                deleteDirectory(file);
//            }
//        }
//        return directory.delete();
//    }
    
    public static void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            Files.walk(directory.toPath())
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
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
    
    public static UsernamePasswordCredentialsProvider promptForCredentials(String repoUrl) {
        Console console = System.console();
        if (console == null) {
            throw new RuntimeException("No console available. Cannot prompt for credentials.");
        }

        System.out.println("Authentication required for: " + repoUrl);
        String username = console.readLine("Username: ");
        char[] passwordArray = console.readPassword("Password: ");
        String password = new String(passwordArray);

        return new UsernamePasswordCredentialsProvider(username, password);
    }
    
}

