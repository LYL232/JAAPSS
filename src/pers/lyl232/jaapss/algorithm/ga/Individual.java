package pers.lyl232.jaapss.algorithm.ga;

import javafx.util.Pair;
import pers.lyl232.jaapss.problem.*;

import java.util.*;

/**
 * 遗传算法个体类
 */
public class Individual {

    /**
     * 包访问权限构造函数
     *
     * @param species 所属物种
     * @param MS      MS基因段
     * @param OS      OS基因段
     */
    Individual(Species species, int[] MS, int[] OS) {
        this.species = species;
        this.MS = MS;
        this.OS = OS;
    }

    /**
     * 复制构造
     *
     * @param other 被复制个体
     */
    Individual(Individual other) {
        int n = other.MS.length;
        this.species = other.species;
        this.MS = new int[n];
        this.OS = new int[n];
        System.arraycopy(other.MS, 0, MS, 0, n);
        System.arraycopy(other.OS, 0, OS, 0, n);
        fitness = other.fitness;
        decoded = other.decoded;
    }

    /**
     * 解码MS和OS基因, 获取基因对应的调度方案
     *
     * @return 调度方案
     */
    synchronized public Schedule decode() {
        if (decoded != null) {
            return decoded;
        }

        Assignment[] assignments = new Assignment[OS.length];
        // 记录每个机器的可用时间:
        HashMap<Integer, Double> machineAvailableTime = new HashMap<>();
        HashMap<Integer, Double> taskAssignedTime = new HashMap<>();
        // 记录每个工件已经出现的个数
        int[] pieceOSCount = new int[species.problem.pieces.size()];

        return decoded = (species.parameter.scheduleRule == ScheduleRule.FORWARD ?
                decodeForward(
                        assignments, machineAvailableTime, taskAssignedTime, pieceOSCount
                ) : decodeBackward(
                assignments, machineAvailableTime, taskAssignedTime, pieceOSCount
        ));
    }

    /**
     * 计算并缓存适应度
     *
     * @return 适应度
     */
    public double getFitness() {
        if (fitness == null) {
            fitness = species.evaluator.evaluate(decode());
        }
        return fitness;
    }

    /**
     * MS段变异: 随机选一个MS段的位置修改其值为任务对应设备组所拥有的设备数之内的一个随机值
     * 不需要检测
     *
     * @param random 随机对象
     */
    synchronized void MSMutate(Random random) {
        int pos = random.nextInt(MS.length);
        MS[pos] = random.nextInt(species.MSRange[pos]);
    }

    /**
     * OS段变异: 随机选择两个位置交换, 注意有可能导致工件之间的依赖错误, 需要进行检测
     *
     * @param random 随机对象
     */
    synchronized void OSMutate(Random random) {
        int pos1 = random.nextInt(OS.length), pos2 = random.nextInt(OS.length),
                temp = OS[pos1];
        OS[pos1] = OS[pos2];
        OS[pos2] = temp;
        GeneChecker.getCurrentGeneChecker().fixOSGene(OS);
    }


    /**
     * 两个父代的MS交叉操作: MS与OS都交叉, OS交叉后会导致基因错误, 需要检错和修复
     *
     * @param p1            父代1
     * @param p2            父代2
     * @param MSCrossRepeat MS基因段重复交叉次数
     * @param random        随机对象
     * @return 子代1, 子代2
     */
    static Pair<Individual, Individual> crossover(
            Individual p1, Individual p2,
            int MSCrossRepeat,
            Random random) {
        int geneLength = p1.MS.length;
        int[] c1OS = new int[geneLength],
                c2OS = new int[geneLength],
                c1MS = new int[geneLength],
                c2MS = new int[geneLength];
        OSCrossover(p1.OS, p2.OS, c1OS, c2OS, random);
        // OS交叉操作有可能导致基因排序错误
        GeneChecker checker = GeneChecker.getCurrentGeneChecker();
        checker.fixOSGene(c1OS);
        checker.fixOSGene(c2OS);
        MSCrossover(p1.MS, p2.MS, c1MS, c2MS, random);

        Individual c1 = new Individual(p1.species, c1MS, c1OS),
                c2 = new Individual(p2.species, c2MS, c2OS);
        // 维护适应度: c1 >= c2
        if (c1.getFitness() < c2.getFitness()) {
            Individual temp = c1;
            c1 = c2;
            c2 = temp;
        }

        // MS段重复交叉(因为开销小), 选择最优的两个子代
        for (int i = 0; i < MSCrossRepeat; ++i) {
            MSCrossover(p1.MS, p2.MS, c1MS, c2MS, random);
            Individual cc1 = new Individual(p1.species, c1MS, c1OS),
                    cc2 = new Individual(p2.species, c2MS, c2OS);
            // 维护c1和c2是最优的两个子代
            if (cc1.getFitness() > c1.getFitness()) {
                c1 = cc1;
            } else if (cc1.getFitness() > c2.getFitness()) {
                c2 = cc1;
            }
            if (cc2.getFitness() > c1.getFitness()) {
                c1 = cc2;
            } else if (cc2.getFitness() > c2.getFitness()) {
                c2 = cc2;
            }
        }

        return new Pair<>(c1, c2);
    }

    /**
     * MS交叉操作: 每个位置随机交换或者不换
     */
    private static void MSCrossover(
            int[] p1, int[] p2, int[] c1, int[] c2,
            Random random
    ) {
        for (int i = 0; i < p1.length; ++i) {
            if (random.nextInt(2) == 0) {
                c1[i] = p1[i];
                c2[i] = p2[i];
            } else {
                c1[i] = p2[i];
                c2[i] = p1[i];
            }
        }
    }

    /**
     * OS交叉操作:
     */
    private static void OSCrossover(
            int[] p1, int[] p2, int[] c1, int[] c2,
            Random random) {
        // OS交叉: 顺序交叉法
        int geneLength = p1.length;
        int pos1 = random.nextInt(geneLength),
                pos2 = random.nextInt(geneLength);
        if (pos1 > pos2) {
            int temp = pos1;
            pos1 = pos2;
            pos2 = temp;
        }

        // 记录每个工件当前执行到的工序
        int[] OSStepCount = new int[geneLength];
        // 记录当前扫描到的位置所对应的工序
        int[] OSStep = new int[geneLength];
        // 填充子代的剩余部分
        OSCrossoverFill(p1, p2, c1, geneLength, OSStepCount, OSStep, pos1, pos2);
        OSCrossoverFill(p2, p1, c2, geneLength, OSStepCount, OSStep, pos1, pos2);

    }

    /**
     * OS交叉过程的子代剩余部分填充部分
     *
     * @param directParent 直接遗传的父代的OS基因段
     * @param fillParent   填充剩余基因的父代的OS基因段
     * @param child        子代OS基因段
     * @param geneLength   基因长度
     * @param OSStepCount  复用数组: 记录当前每个工件已经进行的工序
     * @param OSStep       复用数组: 记录OS每个位置对应的工序
     * @param pos1         直接遗传的起始位置
     * @param pos2         直接遗传的终止位置
     */
    private static void OSCrossoverFill(
            int[] directParent, int[] fillParent, int[] child,
            int geneLength, int[] OSStepCount, int[] OSStep, int pos1, int pos2
    ) {
        Arrays.fill(OSStepCount, 0);
        for (int i = 0; i < geneLength; ++i) {
            OSStep[i] = (OSStepCount[directParent[i]])++;
        }
        // 因为直接遗传避免重复而需要跳过的<工件序号, 工序>集合
        Set<Pair<Integer, Integer>> skipSet = new HashSet<>();
        // 直接遗传的部分
        for (int i = pos1; i <= pos2; ++i) {
            child[i] = directParent[i];
            skipSet.add(new Pair<>(directParent[i], OSStep[i]));
        }

        // 子代下一个需要填充的基因位置
        int childOSPtr = (pos2 + 1 >= geneLength) ? 0 : pos2 + 1;

        Arrays.fill(OSStepCount, 0);

        // 另一个父代从pos2开始填充
        for (int i = pos2 + 1; ; ++i) {
            // 循环
            if (i >= geneLength) {
                i = 0;
            }
            // 如果出现了已经直接遗传的<工件, 工序>, 直接跳过
            if (skipSet.contains(new Pair<>(
                    fillParent[i], OSStepCount[fillParent[i]]++))) {
                continue;
            }

            // 否则遗传这个基因
            child[childOSPtr++] = fillParent[i];
            if (i == pos2) {
                // 循环了一圈
                break;
            }
            if (childOSPtr >= geneLength) {
                childOSPtr = 0;
            }
        }
    }

    /**
     * 正排解码, 传入的参数由decode方法声明
     *
     * @return 调度方案
     */
    synchronized private Schedule decodeForward(
            Assignment[] assignments, HashMap<Integer, Double> machineAvailableTime,
            HashMap<Integer, Double> taskAssignedTime, int[] pieceOSCount
    ) {
        List<Piece> pieces = species.problem.pieces;
        for (int i = 0; i < OS.length; ++i) {
            int pId = OS[i], pieceOrder = pieceOSCount[pId];
            Piece piece = pieces.get(pId);
            Task task = piece.taskList.get(pieceOrder);
            int machineId = task.machineGroup.machines.get(
                    MS[species.MSBegin[pId] + pieceOrder]);

            double beginAt = 0.0;

            for (Task pre : task.getPreTasks()) {
                assert taskAssignedTime.containsKey(pre.id);
                beginAt = Math.max(beginAt, taskAssignedTime.get(pre.id));
            }

            double endAt;
            if (!species.problem.virtualMachineGroups.contains(task.machineGroupId)) {
                // 不是虚拟设备组的
                if (machineAvailableTime.containsKey(machineId)) {
                    beginAt = Math.max(beginAt, machineAvailableTime.get(machineId));
                }
                endAt = beginAt + task.requireTime;
                machineAvailableTime.put(machineId, endAt);
            } else {
                // 虚拟设备组不需要考虑机器冲突
                endAt = beginAt + task.requireTime;
            }
            taskAssignedTime.put(task.id, endAt);
            assignments[i] = new Assignment(task, machineId, beginAt);
            pieceOSCount[pId] += 1;
        }
        return new Schedule(species.problem, assignments);
    }

    /**
     * 倒排解码, 传入的参数由decode方法声明
     *
     * @return 调度方案
     */
    synchronized private Schedule decodeBackward(
            Assignment[] assignments, HashMap<Integer, Double> machineAvailableTime,
            HashMap<Integer, Double> taskAssignedTime, int[] pieceOSCount
    ) {
        List<Piece> pieces = species.problem.pieces;
        // 最早那个任务的分配时间点(负数)
        double earliest = 0.0;
        for (int i = OS.length - 1; i >= 0; --i) {
            int pId = OS[i], pieceOrder = pieceOSCount[pId];
            Piece piece = pieces.get(pId);
            int taskOffset = piece.taskList.size() - 1 - pieceOrder;
            Task task = piece.taskList.get(taskOffset);
            int machineId = task.machineGroup.machines.get(
                    MS[species.MSBegin[pId] + taskOffset]);

            double endAt = 0.0;

            if (task.getSuccessor() != null) {
                endAt = Math.min(endAt, taskAssignedTime.get(task.getSuccessor().id));
            }

            double beginAt;
            if (!species.problem.virtualMachineGroups.contains(task.machineGroupId)) {
                // 不是虚拟设备组的
                if (machineAvailableTime.containsKey(machineId)) {
                    endAt = Math.min(endAt, machineAvailableTime.get(machineId));
                }
                beginAt = endAt - task.requireTime;
                machineAvailableTime.put(machineId, beginAt);
            } else {
                // 虚拟设备组不需要考虑机器冲突
                beginAt = endAt - task.requireTime;
            }
            earliest = Math.min(earliest, beginAt);
            taskAssignedTime.put(task.id, beginAt);
            assignments[i] = new Assignment(task, machineId, beginAt);
            pieceOSCount[pId] += 1;
        }
        // 由于beginAt是负数, 所以需要将其调整为非负数
        for (int i = 0; i < OS.length; ++i) {
            assignments[i].updateBeginAt(assignments[i].getBeginAt() - earliest);
        }
        return new Schedule(species.problem, assignments);
    }

    @Override
    public String toString() {
        return String.format("MS:%s\nOS:%s\n", Arrays.toString(MS),
                Arrays.toString(OS));
    }

    // 个体所属物种
    final private Species species;
    // 基因编码: 分段: MS(机器选择段) OS(工序选择段)
    final private int[] MS, OS;
    // 适应度: 越大越可能被选中, 由所属Species计算
    private Double fitness = null;
    private Schedule decoded = null;
}
