package git.process.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class ProcessExtractor {

    private static final String INPUT_FOLDER = "selected_commits/huge/";
    private static final String OUTPUT_FOLDER = "output";
    private static final String GITHUB_TOKEN = System.getenv("GITHUB_TOKEN"); // Ensure you set this environment variable with your GitHub token

    public static void main(String[] args) throws Exception {
        File inputFolder = new File(INPUT_FOLDER);
        if (!inputFolder.exists() || !inputFolder.isDirectory()) {
            System.out.println("Input folder '" + INPUT_FOLDER + "' does not exist.");
            System.exit(1);
        }

        new File(OUTPUT_FOLDER).mkdirs();

        ObjectMapper mapper = new ObjectMapper();

        for (File file : Objects.requireNonNull(inputFolder.listFiles((dir, name) -> name.endsWith(".csv")))) {
            String projectName = file.getName().replace(".csv", "");
            String repoUrl = "https://github.com/apache/" + projectName + ".git";

//            System.out.println("\n🔍 Processing project: " + projectName);
            
            String repoId = fetchRepoNodeId(projectName);
            System.out.println(repoId + "\t" + repoUrl.replace(".git", ""));
            if (repoId == null) {
                System.err.println("❌ Failed to fetch repo_id for: " + projectName);
                continue;
            } else
            if (1 != 2)
            	continue;

            File localRepo = new File("temp-" + projectName);
            File outputFile = new File(OUTPUT_FOLDER + File.separator + "repo-" + repoId + "-commits");

            try (Git git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(localRepo)
                    .setCloneAllBranches(true)
                    .call();
                 BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, false))) {

                Repository repository = git.getRepository();
                Set<String> commitsOfInterest = readCommitHashes(file.getAbsolutePath());
                Map<String, RevCommit> allCommits = getAllCommits(repository);

                // Actual commits in the repository from the input list
                Set<String> realCommitsToProcess = new HashSet<>(commitsOfInterest);
                realCommitsToProcess.retainAll(allCommits.keySet());

                int totalCommits = realCommitsToProcess.size();
                int processedCommits = 0;

                Set<String> processedCommitIds = new HashSet<>();

                List<Ref> branches = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();

                for (Ref branch : branches) {
                    String branchName = branch.getName()
                            .replace("refs/heads/", "")
                            .replace("refs/remotes/origin/", "");

                    try {
                        // Clean working directory before checkout to avoid conflicts
                        git.clean().setForce(true).setCleanDirectories(true).call();

                        git.checkout()
                                .setName(branchName)
                                .setCreateBranch(true)
                                .setStartPoint(branch.getName())
                                .setForced(true)
                                .call();
                    } catch (Exception e) {
                        System.out.println("⚠️ Skipping branch '" + branchName + "': " + e.getMessage());
                        continue;
                    }

                    for (String commitHash : realCommitsToProcess) {
                        if (processedCommitIds.contains(commitHash)) {
                            continue; // Skip already processed commits
                        }

                        RevCommit commit = allCommits.get(commitHash);
                        if (commit != null) {
                            ObjectNode commitNode = processCommit(repository, commit, mapper, branchName, repoId);
                            writer.write(mapper.writeValueAsString(commitNode));
                            writer.newLine();

                            processedCommitIds.add(commitHash);
                            processedCommits++;
                            printProgress(processedCommits, totalCommits);
                        }
                    }
                }

                System.out.println("\n✅ Output written: " + outputFile.getAbsolutePath());

            } catch (Exception e) {
                System.err.println("❌ Error processing repository " + projectName + ": " + e.getMessage());
            } finally {
                deleteDirectory(localRepo);
            }
        }

        System.out.println("\n🎉 Processing completed.");
    }

    private static void printProgress(int processed, int total) {
        int percent = total > 0 ? (int) ((processed * 100.0f) / total) : 100;
        System.out.print("\rProgress: " + processed + "/" + total + " (" + percent + "%)");
    }

    private static String fetchRepoNodeId(String projectName) {
        String apiUrl = "https://api.github.com/repos/apache/" + projectName;
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Authorization", "token " + GITHUB_TOKEN);
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (InputStream inputStream = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

                    String response = reader.lines().collect(Collectors.joining());
                    ObjectMapper mapper = new ObjectMapper();
                    return mapper.readTree(response).get("node_id").asText();
                }
            } else {
                System.err.println("GitHub API response code: " + responseCode);
            }
        } catch (IOException e) {
            System.err.println("Error fetching repo node ID: " + e.getMessage());
        }
        return null;
    }

    private static Set<String> readCommitHashes(String inputFilePath) throws IOException {
        Set<String> commitHashes = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(inputFilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                commitHashes.add(line.trim());
            }
        }
        return commitHashes;
    }

    private static Map<String, RevCommit> getAllCommits(Repository repository) throws IOException {
        Map<String, RevCommit> commits = new HashMap<>();
        try (RevWalk revWalk = new RevWalk(repository)) {
            for (Ref ref : repository.getAllRefs().values()) {
                try {
                    revWalk.markStart(revWalk.parseCommit(ref.getObjectId()));
                } catch (Exception ignored) {
                    // Skip invalid refs
                }
            }
            for (RevCommit commit : revWalk) {
                commits.put(commit.getName(), commit);
            }
        }
        return commits;
    }

    private static ObjectNode processCommit(Repository repository, RevCommit commit, ObjectMapper mapper, String branchName, String repoId) throws IOException {
        ObjectNode commitNode = mapper.createObjectNode();

        commitNode.put("base_url", "https://github.com");
        commitNode.put("repo_id", repoId);
        commitNode.put("id", commit.getName());
        commitNode.put("branch", branchName);
        commitNode.put("message", commit.getFullMessage());

        ArrayNode parentIds = mapper.createArrayNode();
        for (RevCommit parent : commit.getParents()) {
            parentIds.add(parent.getName());
        }
        commitNode.set("parent_ids", parentIds);

        PersonIdent author = commit.getAuthorIdent();
        ObjectNode authorNode = createPersonNode(mapper, author);
        commitNode.set("author", authorNode);
        commitNode.put("authored_at", author.getWhen().toInstant().toString());

        PersonIdent committer = commit.getCommitterIdent();
        ObjectNode committerNode = createPersonNode(mapper, committer);
        commitNode.set("committer", committerNode);
        commitNode.put("committed_at", committer.getWhen().toInstant().toString());

        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repository);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setDetectRenames(true);

        ArrayNode changesArray = mapper.createArrayNode();
        int additions = 0;
        int deletions = 0;
        int changedFiles = 0;

        if (commit.getParentCount() > 0) {
            List<DiffEntry> diffs = df.scan(getTreeIterator(repository, commit.getParent(0)), getTreeIterator(repository, commit));
            changedFiles = diffs.size();

            for (DiffEntry diff : diffs) {
                ObjectNode change = mapper.createObjectNode();
                change.put("action", diff.getChangeType().toString().toLowerCase());
                change.put("file_path", diff.getNewPath());
                change.put("previous_file_path", diff.getOldPath());

                FileHeader fileHeader = df.toFileHeader(diff);
                EditList edits = fileHeader.toEditList();

                int fileAdditions = 0;
                int fileDeletions = 0;

                for (Edit edit : edits) {
                    fileAdditions += edit.getEndB() - edit.getBeginB();
                    fileDeletions += edit.getEndA() - edit.getBeginA();
                }

                additions += fileAdditions;
                deletions += fileDeletions;

                change.put("additions", fileAdditions);
                change.put("deletions", fileDeletions);

                changesArray.add(change);
            }
        }

        commitNode.put("additions", additions);
        commitNode.put("deletions", deletions);
        commitNode.put("changed_files", changedFiles);
        commitNode.set("changes", changesArray);

        return commitNode;
    }

    private static CanonicalTreeParser getTreeIterator(Repository repository, RevCommit commit) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit targetCommit = walk.parseCommit(commit.getId());
            ObjectId treeId = targetCommit.getTree().getId();

            try (ObjectReader reader = repository.newObjectReader()) {
                CanonicalTreeParser treeParser = new CanonicalTreeParser();
                treeParser.reset(reader, treeId);
                return treeParser;
            }
        }
    }

    private static ObjectNode createPersonNode(ObjectMapper mapper, PersonIdent person) {
        ObjectNode node = mapper.createObjectNode();
        node.put("base_url", "https://github.com");
        node.put("id", Base64.getEncoder().encodeToString(person.getEmailAddress().getBytes(StandardCharsets.UTF_8)));
        node.put("name", person.getName());
        node.put("email", person.getEmailAddress());
        node.put("username", person.getName().toLowerCase().replace(" ", ""));
        return node;
    }

    private static void deleteDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            File[] entries = directory.listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    deleteDirectory(entry);
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("Failed to delete " + directory);
        }
    }
}


//
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ArrayNode;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//import org.eclipse.jgit.api.Git;
//import org.eclipse.jgit.api.ListBranchCommand;
//import org.eclipse.jgit.diff.DiffEntry;
//import org.eclipse.jgit.diff.DiffFormatter;
//import org.eclipse.jgit.lib.*;
//import org.eclipse.jgit.revwalk.*;
//import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
//import org.eclipse.jgit.treewalk.CanonicalTreeParser;
//import org.eclipse.jgit.util.io.DisabledOutputStream;
//
//import java.io.*;
//import java.nio.charset.StandardCharsets;
//import java.text.SimpleDateFormat;
//import java.util.*;
//
//public class ProcessExtractor {
//
//    public static void main(String[] args) throws Exception {
//        File inputFolder = new File("selected_commits");
//        if (!inputFolder.exists() || !inputFolder.isDirectory()) {
//            System.out.println("Input folder 'selected_commits/' does not exist.");
//            System.exit(1);
//        }
//
//        for (File file : Objects.requireNonNull(inputFolder.listFiles((dir, name) -> name.endsWith(".csv")))) {
//            String projectName = file.getName().replace(".csv", "");
//            String repoUrl = "https://github.com/apache/" + projectName + ".git";
//            
//            File localRepo = new File("temp_repos_process/temp-" + projectName);
//            System.out.println("Cloning repository: " + repoUrl);
//
//            try (Git git = Git.cloneRepository()
//                    .setURI(repoUrl)
//                    .setDirectory(localRepo)
//                    .setCloneAllBranches(true)
//                    .call()) {
//
//                Repository repository = git.getRepository();
//                String repoId = Base64.getEncoder().encodeToString(
//						repository.getConfig().getString("remote", "origin", "url").getBytes(StandardCharsets.UTF_8));
//                System.exit(0);
//                System.out.println(repository);
//
//                // Read commits of interest
//                Set<String> commitsOfInterest = readCommitHashes(file.getAbsolutePath());
//
//                // List all branches
//                List<Ref> branches = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
//
//                for (Ref branch : branches) {
//                    String branchName = branch.getName();
//                    System.out.println("Checking out branch: " + branchName);
//                    git.checkout().setName(branchName).setForced(true).call();
//
//                    String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
//                    String outputFolder = projectName + "-" + timestamp;
//
//                    new File(outputFolder).mkdirs();
//
//                    ObjectMapper mapper = new ObjectMapper();
//                    ArrayNode commitsArray = mapper.createArrayNode();
//
//                    Map<String, RevCommit> allCommits = getAllCommits(repository);
//
//                    for (String commitHash : commitsOfInterest) {
//                        RevCommit commit = allCommits.get(commitHash);
//                        if (commit != null) {
//                            ObjectNode commitNode = processCommit(repository, commit, mapper);
//                            commitNode.put("branch", branchName.replace("refs/heads/", ""));
//                            commitsArray.add(commitNode);
//                        }
//                    }
//
//                    if (commitsArray.size() > 0) {
//                        String outputFileName = outputFolder + File.separator + "repo-" + getRepoId(repository) + "-commits.json";
//                        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFileName), commitsArray);
//                        System.out.println("Branch output written: " + outputFileName);
//                    } else {
//                        System.out.println("No matching commits found in branch: " + branchName);
//                    }
//                }
//            } catch (Exception e) {
//                System.err.println("Error processing repository " + projectName + ": " + e.getMessage());
//            } finally {
//                // Clean up
//                deleteDirectory(localRepo);
//            }
//        }
//
//        System.out.println("Processing completed.");
//    }
//
//    private static Map<String, RevCommit> getAllCommits(Repository repository) throws IOException {
//        Map<String, RevCommit> commits = new HashMap<>();
//        try (RevWalk revWalk = new RevWalk(repository)) {
//            for (Ref ref : repository.getAllRefs().values()) {
//                revWalk.markStart(revWalk.parseCommit(ref.getObjectId()));
//            }
//            for (RevCommit commit : revWalk) {
//                commits.put(commit.getName(), commit);
//            }
//        }
//        return commits;
//    }
//
//    private static Set<String> readCommitHashes(String inputFilePath) throws IOException {
//        Set<String> commitHashes = new HashSet<>();
//        try (BufferedReader br = new BufferedReader(new FileReader(inputFilePath))) {
//            String line;
//            while ((line = br.readLine()) != null) {
//                commitHashes.add(line.trim());
//            }
//        }
//        return commitHashes;
//    }
//
//    private static ObjectNode processCommit(Repository repository, RevCommit commit, ObjectMapper mapper) throws IOException {
//        ObjectNode commitNode = mapper.createObjectNode();
//
//        commitNode.put("base_url", "https://github.com");
//        commitNode.put("repo_id", getRepoId(repository));
//        commitNode.put("id", commit.getName());
//        commitNode.put("message", commit.getFullMessage());
//
//        ArrayNode parentIds = mapper.createArrayNode();
//        for (RevCommit parent : commit.getParents()) {
//            parentIds.add(parent.getName());
//        }
//        commitNode.set("parent_ids", parentIds);
//
//        PersonIdent author = commit.getAuthorIdent();
//        ObjectNode authorNode = createPersonNode(mapper, author);
//        commitNode.set("author", authorNode);
//        commitNode.put("authored_at", author.getWhenAsInstant().toString());
//
//        PersonIdent committer = commit.getCommitterIdent();
//        ObjectNode committerNode = createPersonNode(mapper, committer);
//        commitNode.set("committer", committerNode);
//        commitNode.put("committed_at", committer.getWhenAsInstant().toString());
//
//        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
//        df.setRepository(repository);
//
//        ArrayNode changesArray = mapper.createArrayNode();
//        int additions = 0;
//        int deletions = 0;
//        int changedFiles = 0;
//
//        if (commit.getParentCount() > 0) {
//            List<DiffEntry> diffs = df.scan(getTreeIterator(repository, commit.getParent(0)), getTreeIterator(repository, commit));
//            changedFiles = diffs.size();
//
//            for (DiffEntry diff : diffs) {
//                ObjectNode change = mapper.createObjectNode();
//                change.put("action", diff.getChangeType().toString().toLowerCase());
//                change.put("file_path", diff.getNewPath());
//                change.put("previous_file_path", diff.getOldPath());
//
//                // Dummy counts
//                change.put("additions", 0);
//                change.put("deletions", 0);
//
//                changesArray.add(change);
//            }
//        }
//
//        commitNode.put("additions", additions);
//        commitNode.put("deletions", deletions);
//        commitNode.put("changed_files", changedFiles);
//        commitNode.set("changes", changesArray);
//
//        return commitNode;
//    }
//
//    private static CanonicalTreeParser getTreeIterator(Repository repository, RevCommit commit) throws IOException {
//        try (RevWalk walk = new RevWalk(repository)) {
//            RevCommit targetCommit = walk.parseCommit(commit.getId());
//            ObjectId treeId = targetCommit.getTree().getId();
//
//            try (ObjectReader reader = repository.newObjectReader()) {
//                CanonicalTreeParser treeParser = new CanonicalTreeParser();
//                treeParser.reset(reader, treeId);
//                return treeParser;
//            }
//        }
//    }
//
//    private static ObjectNode createPersonNode(ObjectMapper mapper, PersonIdent person) {
//        ObjectNode node = mapper.createObjectNode();
//        node.put("base_url", "https://github.com");
//        node.put("id", Base64.getEncoder().encodeToString(person.getEmailAddress().getBytes(StandardCharsets.UTF_8)));
//        node.put("name", person.getName());
//        node.put("email", person.getEmailAddress());
//        node.put("username", person.getName().toLowerCase().replace(" ", ""));
//        return node;
//    }
//
//    private static String getRepoId(Repository repository) {
//        String remoteUrl = repository.getConfig().getString("remote", "origin", "url");
//        return Base64.getEncoder().encodeToString(remoteUrl.getBytes(StandardCharsets.UTF_8));
//    }
//
//    private static void deleteDirectory(File directory) throws IOException {
//        if (directory.isDirectory()) {
//            File[] entries = directory.listFiles();
//            if (entries != null) {
//                for (File entry : entries) {
//                    deleteDirectory(entry);
//                }
//            }
//        }
//        if (!directory.delete()) {
//            throw new IOException("Failed to delete " + directory);
//        }
//    }
//}
//
////import java.io.BufferedReader;
////import java.io.File;
////import java.io.FileReader;
////import java.io.IOException;
////import java.nio.charset.StandardCharsets;
////import java.util.ArrayList;
////import java.util.Base64;
////import java.util.List;
////
////import org.eclipse.jgit.api.Git;
////import org.eclipse.jgit.diff.DiffEntry;
////import org.eclipse.jgit.diff.DiffFormatter;
////import org.eclipse.jgit.lib.ObjectId;
////import org.eclipse.jgit.lib.ObjectReader;
////import org.eclipse.jgit.lib.PersonIdent;
////import org.eclipse.jgit.lib.Repository;
////import org.eclipse.jgit.revwalk.RevCommit;
////import org.eclipse.jgit.revwalk.RevWalk;
////import org.eclipse.jgit.treewalk.CanonicalTreeParser;
////import org.eclipse.jgit.util.io.DisabledOutputStream;
////
////import com.fasterxml.jackson.databind.ObjectMapper;
////import com.fasterxml.jackson.databind.node.ArrayNode;
////import com.fasterxml.jackson.databind.node.ObjectNode;
////
////public class ProcessExtractor {
//
////	public static void main(String[] args) throws Exception {
////		if (args.length < 2) {
////			System.out.println("Usage: java CommitDataExtractor <repo_path> <commits_input_file>");
////			System.exit(1);
////		}
////		File selected_commits = new File("selected_commits/");
////		for (String filename : selected_commits.list()) {
////			if (new File("selected_commits/" + filename).isDirectory()) {
////				continue;
////			}
////			String projectname = filename.replace(".git", "");
////			String repo_root = "https://github.com/apache/" + projectname + ".git";
////			String repoPath = args[0];
////			String inputFilePath = args[1];
////
////			try (Git git = Git.open(new File(repoPath))) {
////				Repository repository = git.getRepository();
////
////				// Read all commit hashes
////				List<String> commitHashes = readCommitHashes(inputFilePath);
////
////				// Get repository ID (simulate GitHub repo_id format - base64 of repo URL)
////				String repoId = Base64.getEncoder().encodeToString(
////						repository.getConfig().getString("remote", "origin", "url").getBytes(StandardCharsets.UTF_8));
////
////				ObjectMapper mapper = new ObjectMapper();
////				ArrayNode commitsArray = mapper.createArrayNode();
////
////				for (String commitHash : commitHashes) {
////					RevWalk walk = new RevWalk(repository);
////					ObjectId commitId = repository.resolve(commitHash);
////					RevCommit commit = walk.parseCommit(commitId);
////
////					ObjectNode commitNode = mapper.createObjectNode();
////					commitNode.put("base_url", "https://github.com");
////					commitNode.put("repo_id", repoId);
////					commitNode.put("id", commit.getName());
////					commitNode.put("branch", repository.getBranch());
////					commitNode.put("message", commit.getFullMessage());
////
////					// Parent commits
////					ArrayNode parentIds = mapper.createArrayNode();
////					for (RevCommit parent : commit.getParents()) {
////						parentIds.add(parent.getName());
////					}
////					commitNode.set("parent_ids", parentIds);
////
////					// Author
////					PersonIdent author = commit.getAuthorIdent();
////					ObjectNode authorNode = createPersonNode(mapper, author);
////					commitNode.set("author", authorNode);
////					commitNode.put("authored_at", author.getWhen().toInstant().toString());
////
////					// Committer
////					PersonIdent committer = commit.getCommitterIdent();
////					ObjectNode committerNode = createPersonNode(mapper, committer);
////					commitNode.set("committer", committerNode);
////					commitNode.put("committed_at", committer.getWhen().toInstant().toString());
////
////					// Diff stats
////					DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
////					df.setRepository(repository);
////					List<DiffEntry> diffs = df.scan(getTreeIterator(repository, commit.getParent(0)),
////							getTreeIterator(repository, commit));
////
////					int additions = 0;
////					int deletions = 0;
////					int changedFiles = diffs.size();
////					ArrayNode changesArray = mapper.createArrayNode();
////
////					for (DiffEntry diff : diffs) {
////						ObjectNode change = mapper.createObjectNode();
////						change.put("action", diff.getChangeType().toString().toLowerCase());
////						change.put("file_path", diff.getNewPath());
////						change.put("previous_file_path", diff.getOldPath());
////
////						// Dummy data, you can extend with actual line changes
////						change.put("additions", 0);
////						change.put("deletions", 0);
////
////						changesArray.add(change);
////					}
////
////					commitNode.put("additions", additions);
////					commitNode.put("deletions", deletions);
////					commitNode.put("changed_files", changedFiles);
////					commitNode.set("changes", changesArray);
////
////					commitsArray.add(commitNode);
////				}
////
////				// Write output file
////				String outputFileName = "process_metrics/repo-" + repoId + "-commits.json";
////				mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFileName), commitsArray);
////
////				System.out.println("Commit data saved to " + outputFileName);
////			}
////		}
////	}
////
////	private static List<String> readCommitHashes(String inputFilePath) throws IOException {
////		List<String> commitHashes = new ArrayList<>();
////		try (BufferedReader br = new BufferedReader(new FileReader(inputFilePath))) {
////			String line;
////			while ((line = br.readLine()) != null) {
////				commitHashes.add(line.trim());
////			}
////		}
////		return commitHashes;
////	}
////
////	private static CanonicalTreeParser getTreeIterator(Repository repository, RevCommit commit) throws IOException {
////		try (RevWalk walk = new RevWalk(repository)) {
////			RevCommit targetCommit = walk.parseCommit(commit.getId());
////			ObjectId treeId = targetCommit.getTree().getId();
////
////			try (ObjectReader reader = repository.newObjectReader()) {
////				CanonicalTreeParser treeParser = new CanonicalTreeParser();
////				treeParser.reset(reader, treeId);
////				return treeParser;
////			}
////		}
////	}
////
////	private static ObjectNode createPersonNode(ObjectMapper mapper, PersonIdent person) {
////		ObjectNode node = mapper.createObjectNode();
////		node.put("base_url", "https://github.com");
////		node.put("id", Base64.getEncoder().encodeToString(person.getEmailAddress().getBytes(StandardCharsets.UTF_8))); // Simulate
////																														// user
////																														// ID
////		node.put("name", person.getName());
////		node.put("email", person.getEmailAddress());
////		node.put("username", person.getName().toLowerCase().replace(" ", ""));
////		return node;
////	}
////}
