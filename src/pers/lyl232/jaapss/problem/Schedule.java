package pers.lyl232.jaapss.problem;

import com.csvreader.CsvWriter;
import pers.lyl232.jaapss.data.MachineGroup;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 调度信息对象, 一个调度对象表示一个调度结果, 保证所有任务都被分配, 并且
 * 可以对该调度进行分析, 得出其总共用时, 是否合法等结果, 一旦创建一个调度对象,
 * 则该对象不可被更改
 */
public class Schedule {
    final public Assignment[] assignments;

    public Schedule(Problem problem, Assignment[] assignments) {
        this.assignments = assignments;
        this.problem = problem;
    }

    /**
     * @return 错误信息缓存
     */
    public String errorMsg() {
        return error;
    }

    /**
     * debug
     *
     * @return 以设备视角和任务视角输出调度字符串
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        HashMap<Integer, List<Assignment>> machineAssignment = new HashMap<>();
        HashMap<Integer, Assignment> task2Assignment = new HashMap<>();
        // 设备视角:
        for (Assignment assignment : assignments) {
            if (!machineAssignment.containsKey(assignment.machine)) {
                machineAssignment.put(assignment.machine, new ArrayList<>());
            }
            machineAssignment.get(assignment.machine).add(assignment);
            task2Assignment.put(assignment.task.id, assignment);
        }
        builder.append("Machine view:\n");
        for (Map.Entry<Integer, List<Assignment>> entry : machineAssignment.entrySet()) {
            List<Assignment> assignmentList = entry.getValue();
            assignmentList.sort((a1, a2) -> {
                if (a1.getBeginAt() == a2.getBeginAt()) {
                    return 0;
                }
                return a1.getBeginAt() > a2.getBeginAt() ? 1 : -1;
            });
            boolean inVirtualGroup = false;
            for (Integer groupId : problem.virtualMachineGroups) {
                MachineGroup group = problem.machineGroupMap.get(groupId);
                if (group.machineSet.contains(entry.getKey())) {
                    inVirtualGroup = true;
                    builder.append(String.format("VirtualMachineGroup-%d-machine-%d:\n",
                            group.id, entry.getKey()));
                    for (Assignment assignment : assignmentList) {
                        builder.append(String.format("Task-%d[%.2f, %.2f]\n",
                                assignment.task.id, assignment.getBeginAt(), assignment.getEndAt()));
                    }
                    break;
                }
            }
            if (!inVirtualGroup) {
                builder.append(String.format("Machine-%d:", entry.getKey()));
                for (Assignment assignment : assignmentList) {
                    builder.append(String.format("->Task-%d[%.2f, %.2f]",
                            assignment.task.id, assignment.getBeginAt(), assignment.getEndAt()));
                }
                builder.append('\n');
            }
        }
        builder.append("Task view:\n");
        for (Assignment assignment : assignments) {
            if (assignment.task.getSuccessor() == null) {
                builder.append(dfsGetTaskAssignmentDes(task2Assignment, assignment.task)).
                        append("------------------------\n");
            }
        }
        return builder.toString();
    }

    /**
     * 向目的路径输出调度安排(内部表示法, 时刻从0开始, 并且输出所属工件)
     *
     * @param path     路径
     * @param encoding 编码
     * @throws Exception IO异常
     */
    public void toCSVInternal(String path, String encoding)
            throws Exception {
        CsvWriter writer = new CsvWriter(path, ',', Charset.forName(encoding));
        String[] header = {"Task", "Machine", "Piece", "beginAt", "endAt"};
        writer.writeRecord(header);
        HashMap<Integer, Integer> task2Piece = new HashMap<>();
        for (Piece piece : problem.pieces) {
            for (Task task : piece.taskList) {
                task2Piece.put(task.id, piece.id);
            }
        }
        String[] record = new String[5];
        for (Assignment assignment : assignments) {
            record[0] = String.valueOf(assignment.task.id);
            record[1] = String.valueOf(assignment.machine);
            record[2] = String.valueOf(task2Piece.get(assignment.task.id));
            record[3] = String.valueOf(assignment.getBeginAt());
            record[4] = String.valueOf(assignment.getEndAt());
            writer.writeRecord(record);
        }
        writer.close();
    }

    /**
     * 向目的路径输出调度安排(默认表示)
     *
     * @param path     路径
     * @param encoding 编码
     * @throws Exception IO异常
     */
    public void toCSV(String path, String encoding) throws Exception {
        CsvWriter writer = new CsvWriter(path, ',', Charset.forName(encoding));
        String[] header = {"任务id", "后继任务", "计划开始时间", "计划结束时间", "设备id"};
        writer.writeRecord(header);

        DayHourMinute workHours = problem.workHours;

        Calendar calendar = Calendar.getInstance();
        // 获取明天开工的时间
        calendar.add(Calendar.DATE, 1);
        calendar.set(Calendar.HOUR, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        // 任务开始时间戳
        long beginTimestamp = calendar.getTime().getTime(),
                // 每天开始工作的时间相对于当天0点的偏移(毫秒)
                onWorkOffset = (workHours.hour1 * 60 + workHours.minute1) * 60 * 1000,
                // 每天的工作时长(毫秒)
                workHoursTime = workHours.getWorkTime(),
                // 时间单位长度(毫秒)
                timeunit = 1;
        switch (problem.timeunit) {
            case DAY:
                timeunit *= 24;
            case HOUR:
                timeunit *= 60;
            case MINUTE:
                timeunit *= 60;
            case SECOND:
                timeunit *= 1000;
        }


        String[] record = new String[5];
        for (Assignment assignment : assignments) {
            record[0] = String.valueOf(assignment.task.id);
            record[1] = assignment.task.getSuccessor() != null ?
                    String.valueOf(assignment.task.successor.id) : "";
            record[2] = getAssignedDate(
                    assignment.getBeginAt(), timeunit,
                    beginTimestamp, workHoursTime, onWorkOffset, true);
            record[3] = getAssignedDate(
                    assignment.getEndAt(), timeunit,
                    beginTimestamp, workHoursTime, onWorkOffset, false);
            record[4] = String.valueOf(assignment.machine);
            writer.writeRecord(record);
        }
        writer.close();
    }

    /**
     * 分析该调度安排是否合法, 该方法开销较高
     */
    public boolean validate() {
        if (isValid != -1) {
            return isValid == 1;
        }

        if (assignments.length < problem.taskMap.keySet().size()) {
            // 仍有任务没有被分配
            error = "Not all tasks are assigned";
            isValid = 0;
            return false;
        }

        // 干扰测试, 测试检验算法是否能够判断出错误的样例
//        for (int i = 0; i < assignments.length; ++i) {
//            Random random = new Random();
//            Assignment newOne = new Assignment(
//                    assignments[i].task,
//                    assignments[i].machine,
//                    assignments[i].getBeginAt() * (0.5 + random.nextDouble())
//            );
//            assignments[i] = newOne;
//        }

        // 按开始时间排序
        Arrays.sort(assignments, (a1, a2) -> {
            if (a1.getBeginAt() == a2.getBeginAt()) {
                return 0;
            }
            return a1.getBeginAt() > a2.getBeginAt() ? 1 : -1;
        });

        // 已经完成的任务, 用于判断依赖
        HashMap<Integer, Double> finishedTask = new HashMap<>();
        // 每个机器分配到的任务信息
        Map<Integer, List<Assignment>> machineAssignments = new HashMap<>();

        // 判断每个任务的依赖是否满足, 且任务是否被分配到了指定的设备组里
        for (Assignment assignment : assignments) {
            Task task = assignment.task;
            if (!task.machineGroup.machineSet.contains(assignment.machine)) {
                error = String.format("Task-%d is assigned to a wrong machine-%d",
                        task.id, assignment.machine);
                isValid = 0;
                return false;
            }
            for (Task preTask : task.preTasks) {
                Double finishTime = finishedTask.get(preTask.id);
                if (finishTime == null || finishTime - assignment.getBeginAt() > 1e-5) {
                    error = String.format("Task-%d is assigned with unfinished " +
                            "preTask-%d", task.id, preTask.id);
                    isValid = 0;
                    return false;
                }
            }

            if (problem.virtualMachineGroups.contains(task.machineGroupId)) {
                // 虚拟设备组的任务不必考虑机器是否冲突
                finishedTask.put(task.id, assignment.getEndAt());
                continue;
            }
            if (!machineAssignments.containsKey(assignment.machine)) {
                machineAssignments.put(assignment.machine, new ArrayList<>());
            }
            machineAssignments.get(assignment.machine).add(assignment);
            finishedTask.put(task.id, assignment.getEndAt());
        }

        // 判断每台机器是否有冲突分配
        for (List<Assignment> assignments : machineAssignments.values()) {
            assignments.sort((a1, a2) -> {
                if (a1.getBeginAt() == a2.getBeginAt()) {
                    return 0;
                }
                return a1.getBeginAt() > a2.getBeginAt() ? 1 : -1;
            });
            double lastFinished = 0.0;
            for (Assignment assignment : assignments) {
                // 精度
                if (assignment.getBeginAt() - lastFinished < -1e-5) {
                    StringBuilder builder = new StringBuilder();
                    for (Assignment aa : assignments) {
                        if (aa.machine == assignment.machine) {
                            builder.append(aa).append('\n');
                            builder.append(aa.task).append('\n');
                        }
                        if (aa == assignment) {
                            break;
                        }
                    }
                    error = String.format("Task-%d is assigned to a busy Machine-%d\n%s",
                            assignment.task.id, assignment.machine, builder.toString());
                    isValid = 0;
                    return false;
                }
                lastFinished = assignment.getEndAt();
            }
        }

        isValid = 1;
        return true;
    }

    /**
     * 将相对偏移单位的时间转换成真正安排的工作时间
     *
     * @param offsetUnits    相对任务开始时偏移单位
     * @param timeunit       每个偏移单位对应的毫秒数
     * @param beginTimestamp 开始任务的时间毫秒戳
     * @param workHoursTime  每天的工作时间长度(毫秒)
     * @param onWorkOffset   每天开始工作的时间位移
     * @param delay          如果时间点正好在一天的开始或者结束时间点上, 是否将其拖延到后一天
     * @return 真正安排的时间字符串
     */
    static private String getAssignedDate(
            double offsetUnits, double timeunit, long beginTimestamp,
            double workHoursTime, double onWorkOffset, boolean delay) {
        // 相对开始时, 总偏移(ms), 偏移天数, 当天内偏移(ms)
        double offset, day, inDayOffset;
        offset = offsetUnits * timeunit;
        day = Math.floor(offset / workHoursTime);
        inDayOffset = offset - day * workHoursTime;
        if (inDayOffset == 0) {
            if (!delay) {
                inDayOffset = workHoursTime;
                if (workHoursTime + onWorkOffset < 24 * 3600 * 1000 - 1) {
                    // 当天内偏移如果正好等于一天的毫秒数(不可能大于)
                    // 那么会自动将24:00:00换成00:00:00, 此时 day-=1 的日期就不对了,
                    day -= 1;
                }

            }
        }
        return (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(
                beginTimestamp + (long) day * 24 * 3600 * 1000 +
                        (long) onWorkOffset + (long) inDayOffset
        );
    }

    /**
     * debug: toString方法所使用的深度优先搜索搜索任务的依赖链条及其描述
     *
     * @param task2Assignment task到Assignment的映射
     * @param task            搜索节点任务
     * @return 任务依赖链条及其描述
     */
    private String dfsGetTaskAssignmentDes(
            Map<Integer, Assignment> task2Assignment, Task task) {
        StringBuilder builder = new StringBuilder();
        for (Task preTask : task.preTasks) {
            builder.append(dfsGetTaskAssignmentDes(task2Assignment, preTask));
        }
        Assignment assignment = task2Assignment.get(task.id);
        builder.append(String.format("Task{id: %d, Machine: %d, beginAt: %.2f, finishAt: %.2f}\n",
                task.id, assignment.machine, assignment.getBeginAt(), assignment.getEndAt()));
        return builder.toString();
    }

    final private Problem problem;

    private String error = "";
    // 是否合法: -1:未验证, 0:非法, 1:合法
    private int isValid = -1;
}
