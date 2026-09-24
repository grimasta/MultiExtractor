package metrics.extractor;

public class HalsteadMetrics {
    public int operatorsSum;
    public int operandsSum;
    public int operatorsUnique;
    public int operandsUnique;
    public double volume;
    public double difficulty;
    public double effort;
    public double bugProp;
    public double timeRequired;

    public HalsteadMetrics(int operatorsSum, int operandsSum, int operatorsUnique, int operandsUnique,
                           double volume, double difficulty, double effort, double bugProp, double timeRequired) {
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
