package metrics.extractor;

import java.io.Console;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class GitHandler {
    private final String repoUrl;
    private final String projectName;
    private final String tempRepoPath;
    private Git git;

    public GitHandler(String repoUrl, String projectName, String tempRepoPath) {
        this.repoUrl = repoUrl;
        this.projectName = projectName;
        this.tempRepoPath = tempRepoPath;
    }

    public void cloneOrOpen() throws Exception {
        File repoDir = new File(tempRepoPath + projectName + ".git/"); // note: .git for bare repo
        if (repoDir.exists()) {
            System.out.println("Opening existing bare repository for " + projectName);
            this.git = Git.open(repoDir);
        } else {
            try {
                System.out.println("Cloning bare repository for " + projectName);
                this.git = Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(repoDir)
                        .setBare(true) // ✅ bare mode
                        .call();
            } catch (Exception e) {
                if (e.getMessage().contains("not authorized")) {
                    UsernamePasswordCredentialsProvider credentials = promptForCredentials(repoUrl);
                    this.git = Git.cloneRepository()
                            .setURI(repoUrl)
                            .setDirectory(repoDir)
                            .setBare(true)
                            .setCredentialsProvider(credentials)
                            .call();
                } else {
                    throw e;
                }
            }
        }
    }
    
    public Repository getRepository() {
        return this.git.getRepository();
    }

    public Iterable<Ref> listRemoteBranches() throws Exception {
        return git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
    }

    public Iterable<RevCommit> getAllCommits() throws Exception {
        // In bare mode, we use RevWalk to iterate commits
        List<RevCommit> commits = new ArrayList<>();
        try (RevWalk walk = new RevWalk(git.getRepository())) {
            for (Ref ref : git.getRepository().getRefDatabase().getRefsByPrefix("refs/heads/")) {
                ObjectId branchObjectId = ref.getObjectId();
                if (branchObjectId != null) {
                    walk.markStart(walk.parseCommit(branchObjectId));
                }
            }
            for (RevCommit commit : walk) {
                commits.add(commit);
            }
        }
        return commits;
    }

    // No-op in bare mode
    public void checkoutBranch(String shortBranchName, String fullBranchName) {
        System.out.println("[Bare mode] No checkout needed for branch: " + shortBranchName);
    }

    // No-op in bare mode
    public void checkoutCommit(String commitId) {
        System.out.println("[Bare mode] No checkout needed for commit: " + commitId);
    }

    public Git getGit() {
        return git;
    }

    public void cleanUp() {
        if (git != null) {
            git.close();
        }
    }

    private UsernamePasswordCredentialsProvider promptForCredentials(String repoUrl) {
        Console console = System.console();
        if (console == null) throw new RuntimeException("No console available. Cannot prompt for credentials.");
        System.out.println("Authentication required for: " + repoUrl);
        String username = console.readLine("Username: ");
        char[] passwordArray = console.readPassword("Password: ");
        return new UsernamePasswordCredentialsProvider(username, new String(passwordArray));
    }
}