package pers.lyl232.jaapss.problem;

import pers.lyl232.jaapss.data.MachineGroup;
import pers.lyl232.jaapss.data.TaskInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 任务类, 相比任务信息类, 多了方便的引用
 */
public class Task {

    /**
     * @return 后继任务
     */
    public Task getSuccessor() {
        return successor;
    }

    /**
     * @return 前驱任务列表
     */
    public List<Task> getPreTasks() {
        return Collections.unmodifiableList(preTasks);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < preTasks.size(); ++i) {
            builder.append(preTasks.get(i).id);
            if (i < preTasks.size() - 1) {
                builder.append(',');
            }
        }
        builder.append(']');
        return String.format(
                "Task {id: %d, requireTime: %f, " +
                        "expireTime: %.2f, requireTimeEach: %.2f, " +
                        "machineGroup: %d, count: %d, nextTaskId: %d, " +
                        "prepareTime: %d, preTasks: %s}",
                id, requireTime, expireTime, requireTimeEach,
                machineGroupId, count,
                successorId, prepareTime, builder.toString()
        );
    }

    /**
     * @param info         任务对应的信息体
     * @param machineGroup 可用设备组
     */
    Task(TaskInfo info, MachineGroup machineGroup) {
        this.id = info.id;
        this.expireTime = info.expireTime;
        this.machineGroupId = info.machineGroupId;
        this.count = info.count;
        this.successorId = info.successorId;
        this.prepareTime = info.prepareTime;
        this.requireTimeEach = info.requireTimeEach;
        this.machineGroup = machineGroup;
        this.requireTime = this.prepareTime + this.count * this.requireTimeEach;
    }


    final public int id,
            machineGroupId, count, successorId, prepareTime;
    final public double requireTimeEach, requireTime, expireTime;
    // 任务所能使用的设备组
    final public MachineGroup machineGroup;

    // 由于Task创建时并不知全局信息, 所以以下变量需要包访问权限维护
    // 邻接边表示依赖
    final List<Task> preTasks = new ArrayList<>();
    // 后继任务
    Task successor = null;

}
