package pers.lyl232.jaapss.problem;

/**
 * 描述分配类, 表示调度中某个任务在某时分配给某个设备
 */
public class Assignment {
    /**
     * @param task    任务
     * @param machine 分配给的设备
     * @param beginAt 任务开始时间
     */
    public Assignment(Task task, int machine, double beginAt) {
        this.task = task;
        this.machine = machine;
        this.beginAt = beginAt;
        this.endAt = beginAt + task.prepareTime + task.count * task.requireTimeEach;
    }

    @Override
    public String toString() {
        return String.format("Assignment{task: %d, machine: %d, beginAt: %f, " +
                "endAt: %f}", task.id, machine, beginAt, endAt);
    }

    /**
     * 更新分配的开始时间
     *
     * @param beginAt 开始时间
     */
    public void updateBeginAt(double beginAt) {
        this.beginAt = beginAt;
        this.endAt = beginAt + task.prepareTime + task.count * task.requireTimeEach;
    }

    public double getBeginAt() {
        return beginAt;
    }

    public double getEndAt() {
        return endAt;
    }

    final public int machine;
    final public Task task;

    private double beginAt, endAt;
}
