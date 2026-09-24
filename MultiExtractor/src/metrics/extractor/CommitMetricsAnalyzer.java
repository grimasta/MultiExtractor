package metrics.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CommitMetricsAnalyzer {

    private static final String PROJECT_NAME = "sourceCodeMetrics/";
    private static final String TEMP_REPO_PATH = "tempRepo/";

    public static void main(String[] args) throws Exception {
        File commitFolder = new File("selected_commits/original_projects/");

        for (String filename : Objects.requireNonNull(commitFolder.list())) {
            if (new File(commitFolder, filename).isDirectory()) continue;

            String projectName = filename.replace(".csv", "");
            File projectDir = new File(PROJECT_NAME + projectName + "/");
            if (!projectDir.isDirectory()) projectDir.mkdirs();

            List<String> doneHashes = loadDoneHashes(projectName);
            List<String> commitHashes = loadCommitHashes(projectName, doneHashes, "selected_commits/original_projects/");

            if (commitHashes.isEmpty()) {
                System.out.println("No commits left to analyze for project: " + projectName);
                continue;
            }

            String repoUrl = "https://github.com/kde/" + projectName + ".git";
            GitHandler gitHandler = new GitHandler(repoUrl, projectName, TEMP_REPO_PATH);

            try {
                gitHandler.cloneOrOpen();
                Collection<Ref> branches = gitHandler.getRepository().getRefDatabase().getRefs();

                for (Ref branchRef : branches) {
                    String fullBranchName = branchRef.getName();
                    String shortBranchName = fullBranchName.replace("refs/remotes/origin/", "");

                    if (shouldSkipBranch(shortBranchName)) continue;

                    System.out.println("Analyzing branch: " + shortBranchName);
//                    gitHandler.checkoutBranch(shortBranchName, fullBranchName);
                    analyzeCommits(gitHandler, commitHashes, projectName);
                }
            } finally {
                gitHandler.cleanUp();
            }
        }
    }

    private static List<String> loadDoneHashes(String projectName) {
        List<String> doneHashes = new ArrayList<>();
        File doneCommitFolder = new File(PROJECT_NAME + projectName + "/");
        if (doneCommitFolder.exists()) {
            for (String fileName : Objects.requireNonNull(doneCommitFolder.list())) {
                doneHashes.add(fileName.replace(".json", ""));
            }
        }
        return doneHashes;
    }
    
    private static List<String> loadCommitHashes(String projectName, List<String> doneHashes, String commitFileLocation) {
        List<String> commitHashes = new ArrayList<>();
        File commitFile = new File(commitFileLocation + projectName + ".csv");
        try (BufferedReader fileReader = new BufferedReader(new FileReader(commitFile))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                String commitHash = line.strip();
                if (!doneHashes.contains(commitHash)) {
                    commitHashes.add(commitHash);
                }
            }
            System.out.println("Succesfully Loaded Commit Hashes for " + projectName);
        } catch (IOException e) {
            System.out.println("Problem reading commit selection file for " + projectName);
        }
        return commitHashes;
    }

    private static boolean shouldSkipBranch(String branchName) {
        List<String> skipBranches = Arrays.asList("asf-site", "branch-3.0", "branch-3.1", "branch-3.2");
        if (skipBranches.contains(branchName)) {
            System.out.println("Branch " + branchName + " was skipped by the user.");
            return true;
        }
        return false;
    }

    private static void analyzeCommits(GitHandler gitHandler, List<String> commitHashes, String projectName) throws Exception {
        Iterable<RevCommit> commits = gitHandler.getAllCommits();

        for (RevCommit commit : commits) {
            String commitId = commit.getName();
            if (!commitHashes.contains(commitId)) continue;

            try {
                System.out.println("Analyzing commit: " + commitId);
//                gitHandler.checkoutCommit(commitId);
                analyzeAllFilesInSystem(gitHandler, commitId, projectName);
                commitHashes.remove(commitId);
            } catch (Exception e) {
                System.err.println("Error analyzing commit " + commitId + ": " + e.getMessage());
            }
        }
    }

    private static void analyzeAllFilesInSystem(GitHandler gitHandler, String commitId, String projectName) throws IOException {
        Git git = gitHandler.getGit();
        ObjectId treeId = git.getRepository().resolve(commitId + "^{tree}");
        TreeWalk treeWalk = new TreeWalk(git.getRepository());
        treeWalk.addTree(treeId);
        treeWalk.setRecursive(true);
        treeWalk.setFilter(TreeFilter.ALL);

        Map<String, FileMetrics> fileMetricsMap = new ConcurrentHashMap<>();

        while (treeWalk.next()) {
            String rawFilePath = treeWalk.getPathString();
            if (rawFilePath.contains("?") || rawFilePath.contains(":") || rawFilePath.contains("*") ||
                rawFilePath.contains("<") || rawFilePath.contains(">") || rawFilePath.contains("|")) {
                System.err.println("Skipping problematic path: " + rawFilePath);
                continue;
            }

            String filePath = sanitizeFilePath(rawFilePath);
            if (!isSupportedFileType(filePath)) continue;
            try {
                byte[] fileBytes = git.getRepository().open(treeWalk.getObjectId(0)).getBytes();
                String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
                fileMetricsMap.put(filePath, MetricCalculator.calculateMetrics(fileContent, filePath));
            } catch (Exception e) {
                System.err.println("Error reading file " + filePath + ": " + e.getMessage());
            }
        }

        MetricsExporter.saveCommitStatisticsToFile(fileMetricsMap, commitId, projectName);
    }

    private static String sanitizeFilePath(String filePath) {
        return filePath.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static boolean isSupportedFileType(String filePath) {
        return filePath.endsWith(".java") || filePath.endsWith(".py") || filePath.endsWith(".c") || filePath.endsWith(".cpp") || filePath.endsWith(".h") ||
               filePath.endsWith(".cs") || filePath.endsWith(".rb") || filePath.endsWith(".pl") || filePath.endsWith(".js") || filePath.endsWith(".ts");
    }
}
