package pers.lyl232.jaapss.data;

/**
 * 任务信息类, 只存储数据
 */
public class TaskInfo {
    /**
     * @param id               任务id
     * @param expireTime       预期时间
     * @param requireTimeEach 每个单位任务的运行时间
     * @param machineGroupId   所需设备
     * @param count            单位任务数量
     * @param successorId       后继任务id
     * @param prepareTime      准备时间
     */
    public TaskInfo(int id, double expireTime, double requireTimeEach, int machineGroupId
            , int count, int successorId, int prepareTime) {
        this.id = id;
        this.expireTime = expireTime;
        this.requireTimeEach = requireTimeEach;
        this.machineGroupId = machineGroupId;
        this.count = count;
        this.successorId = successorId;
        this.prepareTime = prepareTime;
    }

    final public int id, machineGroupId, count, successorId, prepareTime;
    final public double requireTimeEach, expireTime;
}
