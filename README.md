# MultiExtractor

MultiExtractor is a Java toolkit for mining software repositories. It collects three kinds of data about open-source projects and writes them out in a form suitable for downstream processing (for example, loading into a database for fault-proneness research):

1. **Source-code metrics** per file, per commit (LOC, cyclomatic complexity, Halstead measures, fan-in/fan-out, maintainability index), with per-commit summary statistics.
2. **Process metrics** per commit (authors, committers, parents, additions/deletions, changed files) as JSON Lines.
3. **Issue-tracker data** (Bugzilla and GitLab issues with comments, plus the files mentioned in them and the dates they were reported).

> **Status:** research code. The current tools write JSON files to disk. There is no database loader in this repository yet, so the "store in a database" step happens downstream.

---

## Repository layout

The code is organised into four packages.

| Package | Class | Purpose |
|---|---|---|
| `metrics.extractor` | `CommitMetricsAnalyzer` | Main entry point for source-metric extraction. Reads commit hashes from `selected_commits/original_projects/<project>.csv`, opens a bare clone of each repository, and computes metrics for every supported file in each selected commit's tree. Skips already-processed commits and a configurable list of branches. |
| | `GitHandler` | Clones (bare) or opens a repository with JGit; falls back to prompting for a username/password if the remote answers "not authorized". |
| | `MetricCalculator` | Computes the per-file metrics (see below). |
| | `FileMetrics`, `HalsteadMetrics` | Value classes holding the results. |
| | `MetricsExporter` | Writes one JSON file per commit with per-file metrics and mean / max / min / median / sd statistics. |
| | `CommitMetricsAnalyzer1take` ... `4take` | Earlier iterations of the analyzer, kept for reference (see [Legacy classes](#legacy-classes)). |
| `git.process.extractor` | `ProcessExtractor` | Extracts commit-level process metadata for the commits listed in `selected_commits/huge/*.csv` and writes JSON Lines to `output/`. |
| `trackers.samples` | `BugzillaBugReportsFetcher` | Downloads bugs (with comments) from the KDE Bugzilla REST API by product/keyword. |
| | `BugzillaKeywordSearchDownloader` | Downloads bugs from KDE Bugzilla by `keywords` field. |
| | `GitlabIssuesDownloader`, `GitlabIssuesDownloader2` | Download issues from `gitlab.gnome.org`; version 2 paginates and attaches notes (comments). |
| | `GitLabCommitsDownloader` | Despite its name, it calls the **GitHub** API and saves commit SHAs and timestamps per repository. |
| `trackers.file.extractor` | `BugzillaReportsProcessor` | Scans downloaded Bugzilla reports for file mentions (patch-style `+N -M path` lines) and groups them by report creation date. |
| | `GitLabIssueFileExtractor` | Same idea for GitLab issues, using a looser file-path regex. |
| `bugzilla.project.info` | `BugzillaProjectReport` | Prints total and resolved bug counts for every accessible product on KDE Bugzilla. |

## Metrics computed

For each supported source file (`.java .py .c .cpp .h .cs .rb .pl .js .ts`) `MetricCalculator` produces:

- `loc`, `comment_ratio`
- `cyclomatic_complexity` (regex count of `if`, `for`, `while`, `case`, plus 1)
- Halstead: `volume`, `difficulty`, `effort`, `bugprop`, `timerequired`, and operator/operand totals and unique counts
- `fanout_external`, `fanout_internal`, `fanin_external`, `fanin_internal` (line-pattern heuristics, not real dependency analysis)
- `maintainability_index`

These are language-agnostic, text-based approximations. They are fast and uniform across languages, but they are not a substitute for parser-based tools.

Output per commit (`sourceCodeMetrics/<project>/<commit>.json`):

```json
{
  "files": { "<path>": { "loc": 120, "cyclomatic_complexity": 14, "...": 0 } },
  "stats": { "mean": { "loc": 0 }, "max": {}, "min": {}, "median": {}, "sd": {} }
}
```

## Requirements

- JDK 11 or newer (the build targets Java 11; the code uses `String.strip()`)
- Maven 3.x. Sources are under `src/` (set via `<sourceDirectory>` in `pom.xml`), and the Maven artifact is still named `IssueTrackerExtractor2`.
- Dependencies (declared in `pom.xml`):

| Library | Version | Used for |
|---|---|---|
| `org.eclipse.jgit:org.eclipse.jgit` | 7.1.0.202411261347-r | Cloning repositories, walking commits and trees, diffs |
| `com.fasterxml.jackson.core:jackson-databind` | 2.18.2 | JSON output |
| `org.json:json` | 20250107 | Tracker downloaders and processors |
| `com.github.javaparser:javaparser-core` | 3.26.3 | Only imported by `CommitMetricsAnalyzer4take` |
| `org.slf4j:slf4j-api` | 2.0.0 | Logging API used by JGit |
| `org.slf4j:slf4j-simple` | 2.0.0 (test scope) | Logging binding |

- Network access to GitHub, `gitlab.gnome.org` and `bugs.kde.org`
- Enough disk space for bare clones under `tempRepo/`

## Configuration and credentials

Several tools need API tokens. **Do not commit tokens to source control.** Read them from the environment instead of hard-coding them, for example:

```java
String token = System.getenv("GITHUB_TOKEN");   // or GITLAB_API_TOKEN
```

Suggested variables (nothing is read from the environment yet, so add the `System.getenv` calls where the constants used to be): `GITHUB_TOKEN` (`ProcessExtractor`, `GitLabCommitsDownloader`) and `GITLAB_API_TOKEN` (`GitlabIssuesDownloader`, `GitlabIssuesDownloader2`).

`GitHandler` prompts on the console for a username and password when a clone is refused, so run it from a real terminal rather than from an IDE console that has no `System.console()`.

## Usage

Inputs are plain CSV files with one commit hash per line, named after the project.

```
selected_commits/
  original_projects/<project>.csv   # used by CommitMetricsAnalyzer
  huge/<project>.csv                # used by ProcessExtractor
```

1. **Source metrics**
   ```
   java -cp <classpath> metrics.extractor.CommitMetricsAnalyzer
   ```
   Results go to `sourceCodeMetrics/<project>/<commit>.json`. Re-running resumes: commits that already have a JSON file are skipped. The repository URL is currently built as `https://github.com/kde/<project>.git`; edit `CommitMetricsAnalyzer` to change the organisation.

2. **Process metrics**
   ```
   java -cp <classpath> git.process.extractor.ProcessExtractor
   ```
   Results go to `output/repo-<node_id>-commits` (JSON Lines). The repository URL is built as `https://github.com/apache/<project>.git`.

3. **Issue trackers**
   - Download: run `BugzillaBugReportsFetcher`, `BugzillaKeywordSearchDownloader`, `GitlabIssuesDownloader2` or `GitLabCommitsDownloader`. Edit the `projectIds` / `repos` arrays in each `main` to choose projects.
   - Post-process: run `BugzillaReportsProcessor` (reads `bugzilla\<project>-reports.json`, writes `bugzillaFilesWithDates\<project>.json`) or `GitLabIssueFileExtractor` (reads `gitlab_with_comments\<project>_issues.json`, writes `FilesWithDates\<project>.json`).

4. **Bugzilla overview**
   ```
   java -cp <classpath> bugzilla.project.info.BugzillaProjectReport
   ```

Project lists, input/output paths and the skipped-branch list are hard-coded in each class's `main`, so adjust them before running.

## Known limitations

- **No database layer.** Everything is written to the local file system.
- **Hard-coded configuration.** Project names, paths and branch skip-lists live in the source. Some paths use Windows separators (`bugzilla\\`, `gitlab_with_comments\\`).
- **`ProcessExtractor` currently stops after looking up the repository ID.** A leftover `if (1 != 2) continue;` in `main` skips the clone-and-extract step for every project. Remove it to enable full extraction.
- **Bugzilla queries are not paginated** and can hit server limits on large products; `GitlabIssuesDownloader` (v1) fetches only the first page of issues. Use `GitlabIssuesDownloader2` for full downloads.
- **Metric heuristics are approximate** (see [Metrics computed](#metrics-computed)).
- **Non-UTF-8 files** are decoded as UTF-8 and may yield noisy metrics.

## Legacy classes

`CommitMetricsAnalyzer1take` to `4take` are earlier experiments and are superseded by `CommitMetricsAnalyzer` plus the shared `MetricCalculator`, `MetricsExporter` and `GitHandler`. They duplicate inner `FileMetrics` and `HalsteadMetrics` classes and can be moved to an `archive/` package or removed.

## Contributing / housekeeping

- Consider renaming the Maven `groupId`/`artifactId` (currently `IssueTrackerExtractor2`) to match the project name.
- `slf4j-simple` is test-scoped, so a normal run has no SLF4J binding and JGit logging is silently dropped. Change its scope to `compile` or `runtime` if you want log output.
- Add a `.gitignore` for `tempRepo/`, `sourceCodeMetrics/`, `output/` and any credentials files.
- Revoke any token that was ever written into a source file, even if it was never committed.