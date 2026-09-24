package metrics.extractor;

import java.util.HashMap;
import java.util.Map;

public class FileMetrics {
    public double commentRatio;
    public int loc;
    public int cyclomaticComplexity;
    public double halsteadVolume;
    public double halsteadDifficulty;
    public double halsteadEffort;
    public double halsteadBugProp;
    public double halsteadTimeRequired;
    public int operandsSum;
    public int operandsUnique;
    public int operatorsSum;
    public int operatorsUnique;
    public int fanoutExternal;
    public int fanoutInternal;
    public int faninExternal;
    public int faninInternal;
    public double maintainabilityIndex;

    public Map<String, Double> toMap() {
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
