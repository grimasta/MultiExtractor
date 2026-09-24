package metrics.extractor;

import java.util.*;

public class MetricCalculator {

    public static FileMetrics calculateMetrics(String fileContent, String filePath) {
        FileMetrics metrics = new FileMetrics();
        String[] lines = fileContent.split("\\n");
        int loc = lines.length;
        int commentLines = (int) Arrays.stream(lines)
                .filter(line -> line.trim().startsWith("//") || line.trim().startsWith("/*") || line.trim().contains("#") || line.trim().startsWith("--"))
                .count();
        int cyclomaticComplexity = calculateCyclomaticComplexity(fileContent);
        HalsteadMetrics halstead = calculateHalsteadMetrics(fileContent);

        metrics.loc = loc;
        metrics.commentRatio = loc > 0 ? (double) commentLines / loc * 100 : 0.0;
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
        return (int) Arrays.stream(content.split("\\n"))
                .filter(line -> line.startsWith("import") || line.contains("System.out") || line.contains("require") || line.contains("#include"))
                .count();
    }

    private static int calculateInternalFanout(String content) {
        return (int) Arrays.stream(content.split("\\n"))
                .filter(line -> line.contains("this.") || line.contains("new "))
                .count();
    }

    private static int calculateExternalFanin(String content) {
        return (int) Arrays.stream(content.split("\\n"))
                .filter(line -> line.contains("public") && line.contains("("))
                .count();
    }

    private static int calculateInternalFanin(String content) {
        return (int) Arrays.stream(content.split("\\n"))
                .filter(line -> line.contains("private") && line.contains("("))
                .count();
    }

    private static int calculateCyclomaticComplexity(String fileContent) {
        int complexity = 1; // Start with 1 for the default path
        complexity += countOccurrences(fileContent, "if\\s*\\(")
                + countOccurrences(fileContent, "for\\s*\\(")
                + countOccurrences(fileContent, "while\\s*\\(")
                + countOccurrences(fileContent, "case ");
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
            } else if (!token.isEmpty()) {
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
        double volume = vocabulary > 0 ? length * (Math.log(vocabulary) / Math.log(2)) : 0;
        double difficulty = n2 > 0 ? (n1 / 2.0) * (N2 / n2) : 0;
        double effort = volume * difficulty;
        double bugProp = volume / 3000.0;
        double timeRequired = effort / 18.0;

        return new HalsteadMetrics(operatorsSum, operandsSum, uniqueOperators.size(), uniqueOperands.size(), volume, difficulty, effort, bugProp, timeRequired);
    }

    private static double calculateMaintainabilityIndex(FileMetrics metrics) {
        if (metrics.loc == 0 || metrics.halsteadVolume == 0) return 0.0;
        double mi = 171 - 5.2 * Math.log(metrics.halsteadVolume)
                - 0.23 * metrics.cyclomaticComplexity
                - 16.2 * Math.log(metrics.loc);
        return Math.min(100, Math.max(0, mi));
    }

    private static int countOccurrences(String content, String regex) {
        return content.split(regex).length - 1;
    }

    private static boolean isOperator(String token) {
        return Arrays.asList("+", "-", "*", "/", "%", "&&", "||", "!", "<", ">", "==", "!=").contains(token);
    }
}
