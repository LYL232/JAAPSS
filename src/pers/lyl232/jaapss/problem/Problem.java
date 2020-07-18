package pers.lyl232.jaapss.problem;

import pers.lyl232.jaapss.data.MachineGroup;
import pers.lyl232.jaapss.data.TaskInfo;
import pers.lyl232.jaapss.data.loader.DataLoadException;
import pers.lyl232.jaapss.data.loader.MachineGroupLoader;
import pers.lyl232.jaapss.data.loader.TaskInfoLoader;

import java.util.*;

/**
 * 问题类, 单例, 负责加载问题的静态描述, 表示易于实现的数据结构,
 * 不会在求解过程中改变
 */
public class Problem {
    // 任务id到任务对象的映射
    final public Map<Integer, Task> taskMap;
    // 所有注册的设备组: groupId->MachineGroup, 所有设备都有的组的id是-1
    final public Map<Integer, MachineGroup> machineGroupMap;
    // 拓扑排序后的工件列表, 工件id对应list下标
    final public List<Piece> pieces;
    // 虚拟设备组: 不需要等待时间, 有需求立即能满足的设备
    final public Set<Integer> virtualMachineGroups = new HashSet<>();
    // 每个工件的前驱个数
    final public int[] piecesDependencyCount;
    // 每个工件的后继工件, 因为工件的id是从0开始连续的, 所以可以为了提升性能将其放入数组中
    final public Piece[] piecesSuc;
    // 表示工作时间
    final public DayHourMinute workHours;
    final public TimeUnit timeunit;

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Problem:\n");
        builder.append("machine groups:\n");
        for (Map.Entry<Integer, MachineGroup> entry : machineGroupMap.entrySet()) {
            builder.append(String.format("%d: %s\n", entry.getKey(), entry.getValue().toString()));
        }
        builder.append("tasks:\n");
        for (Map.Entry<Integer, Task> entry : taskMap.entrySet()) {
            builder.append(String.format("%d: %s\n", entry.getKey(), entry.getValue().toString()));
        }
        builder.append("pieces:\n");
        for (Piece piece : pieces) {
            builder.append(String.format("%d: %s\n", piece.id, piece.toString()));
        }
        return builder.toString();
    }

    /**
     * 默认一天24小时, 时间单位为分钟
     *
     * @param machineLoader 机器信息加载器
     * @param taskLoader    任务信息加载器
     * @throws Exception 加载异常, 数据一致性异常等
     */
    public Problem(MachineGroupLoader machineLoader, TaskInfoLoader taskLoader)
            throws Exception {
        this(machineLoader, taskLoader, new DayHourMinute(0, 0, 24, 0), TimeUnit.MINUTE);
    }

    /**
     * @param machineLoader 机器信息加载器
     * @param taskLoader    任务信息加载器
     * @param workHours     工作时间, 如果为null则表示24小时工作
     * @param timeUnit      时间单位
     * @throws Exception 加载异常, 数据一致性异常等
     */
    public Problem(
            MachineGroupLoader machineLoader, TaskInfoLoader taskLoader,
            DayHourMinute workHours, TimeUnit timeUnit)
            throws Exception {
        if (taskLoader == null || machineLoader == null) {
            throw new DataLoadException("Please define taskLoader and machineLoader");
        }
        this.workHours = workHours;
        this.timeunit = timeUnit;
        // 维护只读属性
        machineGroupMap = Collections.unmodifiableMap(machineLoader.load());
        taskMap = Collections.unmodifiableMap(initializeTasks(taskLoader.load()));
        pieces = Collections.unmodifiableList(initializePieces());
        // 工件额外维护信息
        piecesSuc = new Piece[pieces.size()];
        Arrays.fill(piecesSuc, null);
        piecesDependencyCount = new int[pieces.size()];
        Arrays.fill(piecesDependencyCount, 0);
        for (Piece piece : pieces) {
            piecesSuc[piece.id] = piece.getSuccessor();
            for (Piece pre : piece.predecessor) {
                piecesDependencyCount[piece.id] += pre.taskList.size();
            }
        }
    }

    /**
     * 初始化工件
     *
     * @return pieces
     */
    private List<Piece> initializePieces() {
        // 筛掉入度不为0的任务
        HashSet<Integer> sucTasks = new HashSet<>();
        for (Task task : taskMap.values()) {
            for (Task preTask : task.preTasks) {
                sucTasks.add(preTask.id);
            }
        }

        List<Piece> pieces = new ArrayList<>();

        for (Task task : taskMap.values()) {
            if (!sucTasks.contains(task.id)) {
                dfsConvertTask2Piece(pieces, task);
            }
        }

        // 维护每个工件的后继工件
        for (Piece piece : pieces) {
            for (Piece pre : piece.predecessor) {
                pre.successor = piece;
            }
        }

        return pieces;
    }

    /**
     * 初始化Task信息
     *
     * @return Task Map
     */
    private Map<Integer, Task> initializeTasks(Map<Integer, TaskInfo> taskInfoMap)
            throws DataConsistencyException {
        Map<Integer, Task> taskMap = new HashMap<>();
        // 注册全局信息
        for (TaskInfo info : taskInfoMap.values()) {
            MachineGroup group = machineGroupMap.get(info.machineGroupId);
            if (group == null) {
                throw new DataConsistencyException(String.format("MachineGroup-%d not found",
                        info.machineGroupId));
            }
            taskMap.put(info.id, new Task(info, group));
        }
        // 验证是否存在一个拓扑排序, 使得所有任务能够得到完成
        // 并求每个任务的前驱后继

        // 每个任务的依赖入度 id -> inDegree
        Map<Integer, Integer> inDegree = new HashMap<>();

        for (Task task : taskMap.values()) {
            if (task.successorId != -1) {
                Task successor = taskMap.get(task.successorId);
                if (successor == null) {
                    throw new DataConsistencyException(String.format(
                            "Cannot find successor of task-%d with id %d",
                            task.id, task.successorId
                    ));
                }
                successor.preTasks.add(task);
                task.successor = successor;
                Integer in = inDegree.get(successor.id);
                if (in == null) {
                    in = 0;
                }
                inDegree.put(successor.id, in + 1);
            }
        }

        // 求拓扑排序
        Queue<Integer> queue = new LinkedList<>();
        for (Task task : taskMap.values()) {
            if (!inDegree.containsKey(task.id)) {
                queue.add(task.id);
            }
        }

        // 可以完成任务集合
        Set<Integer> finishedSet = new HashSet<>();
        while (!queue.isEmpty()) {
            Task task = taskMap.get(queue.poll());
            assert task != null;
            finishedSet.add(task.id);
            if (task.successorId != -1) {
                int in = inDegree.get(task.successorId);
                if (in == 1) {
                    queue.add(task.successorId);
                }
                inDegree.put(task.successorId, in - 1);
            }
        }

        for (Task task : taskMap.values()) {
            if (!finishedSet.contains(task.id)) {
                throw new DataConsistencyException(String.format(
                        "can not solve: Task-%d has self dependency", task.id
                ));
            }
        }

        return taskMap;
    }

    /**
     * dfs新建并按拓扑排序注册工件
     *
     * @param pieces 已经注册的工件列表
     * @param task   新工件的入口任务
     * @return 新注册的工件
     */
    static Piece dfsConvertTask2Piece(List<Piece> pieces, Task task) {
        List<Task> piecesTasks = new ArrayList<>();
        piecesTasks.add(task);
        while (task.preTasks.size() == 1) {
            piecesTasks.add(task.preTasks.get(0));
            task = task.preTasks.get(0);
        }
        List<Piece> prePieces = new ArrayList<>();
        if (task.preTasks.size() > 1) {
            // 依赖分叉, 说明该工件有前驱工件
            for (Task dfsTask : task.preTasks) {
                prePieces.add(dfsConvertTask2Piece(pieces, dfsTask));
            }
        }
        Collections.reverse(piecesTasks);
        Piece newOne = new Piece(pieces.size(), piecesTasks, prePieces);
        pieces.add(newOne);
        return newOne;
    }

}
