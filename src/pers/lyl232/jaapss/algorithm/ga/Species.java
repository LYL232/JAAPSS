package pers.lyl232.jaapss.algorithm.ga;

import pers.lyl232.jaapss.algorithm.ParameterException;
import javafx.util.Pair;
import pers.lyl232.jaapss.problem.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 物种类: 管理遗传算法运行
 */
public class Species {
    // 表示算法参数
    public static class Parameter {
        // 种群大小, 迭代次数
        public int population = 200, maxGeneration = 100;
        // MS段基因重复交叉的次数(每一次交叉多产生几个子代, 选最优的两个保留, 越大越有利于多线程的CPU资源利用)
        public int MSCrossoverRepeat = 10;
        // 变异概率, 交叉概率
        public double mutateRate = 0.01, crossoverRate = 0.6;
        // 锦标赛策略中二选一选择更好的那个的概率
        public double selectBetterRate = 0.8;
        // 全局随机种子
        public long seed = System.currentTimeMillis();
        public ScheduleStrategy scheduleStrategy = ScheduleStrategy.LEAST_EXCEED_TIME;
        public ScheduleRule scheduleRule = ScheduleRule.FORWARD;
        public int workers = Runtime.getRuntime().availableProcessors();
        // debug: 每次生成新的种群, 都会检查其是否为可行解, 增加了开销
        public boolean debug = false;
    }

    /**
     * 根据问题构造环境
     *
     * @param problem   问题
     * @param parameter 参数
     */
    public Species(Problem problem, Parameter parameter) throws Exception {
        checkParameters(parameter);

        // 初始化非线程安全类, 基因检测器
        GeneChecker.initializeCurrentGeneChecker(problem);

        this.problem = problem;
        this.parameter = parameter;
        globalRandom = new Random(parameter.seed);
        geneLength = problem.taskMap.size();

        if (geneLength < 1) {
            throw new DataConsistencyException("Error: No task to assign.");
        }

        MSBegin = new int[problem.pieces.size()];
        MSBegin[0] = 0;

        for (int i = 1; i < problem.pieces.size(); ++i) {
            MSBegin[i] = MSBegin[i - 1] + problem.pieces.get(i - 1).taskList.size();
        }

        MSRange = new int[geneLength];
        OSGeneTemplate = new int[geneLength];
        int count = 0;
        for (int i = 0; i < problem.pieces.size(); ++i) {
            Piece piece = problem.pieces.get(i);
            for (int j = 0; j < piece.taskList.size(); ++j) {
                OSGeneTemplate[count++] = piece.id;
                MSRange[MSBegin[i] + j] = piece.taskList.get(j).machineGroup.machines.size();
            }
        }

        evaluator = ScheduleEvaluator.get(problem, parameter.scheduleStrategy, parameter.scheduleRule);

        individuals = new Individual[parameter.population];

        crossovered = new boolean[parameter.population];

        queueCondition = queueLock.newCondition();

    }

    /**
     * 启动
     *
     * @param verbose: 是否打印每一代的统计信息:
     */
    public Individual start(boolean verbose) throws Exception {
        initialize();

        best = individuals[0];

        for (int i = 0; i < parameter.maxGeneration; ++i) {

            decodeIndividuals();

            // 验证best解的可行性
            if (!best.decode().validate()) {
                // 如果最佳个体解不可行(有bug)
                best = null;
                for (int j = 0; j < individuals.length; ++i) {
                    if (!individuals[j].decode().validate()) {
                        // 抛弃无效个体
                        individuals[i] = newRandomIndividual();
                    } else {
                        // 选择第一个(因为精英策略)有效的个体
                        best = individuals[i];
                        break;
                    }
                }
                if (best == null) {
                    // 所有个体均无效
                    best = newRandomIndividual();
                }
            }

            if (verbose) {
                System.out.printf(
                        "generation-%d: best fitness: %f, average fitness: %f\n",
                        i, best.getFitness(),
                        fitnessSum / individuals.length);
            }

            if (Math.abs(best.getFitness() - evaluator.getOptimized()) < 1e-5) {
                // 终止条件: 适应度达到极限值
                if (verbose) {
                    System.out.println("best fitness reach limit, stopping.");
                }
                break;
            }

            Random generationRandom = new Random(globalRandom.nextLong());
            selection(generationRandom);
            crossover(generationRandom);
            mutate(generationRandom);
        }

        if (parameter.workers > 1) {
            for (int i = 0; i < workers.length; ++i) {
                workQueue.add(WorkType.EXIT);
            }
            queueLock.lock();
            queueCondition.signalAll();
            queueLock.unlock();

            for (Worker worker : workers) {
                worker.join();
            }
        }
        if (!best.decode().validate()) {
            // 如果解不可行
            // 先按适应度由大到小排序
            Arrays.sort(individuals, (i1, i2) -> {
                if (i1.getFitness() == i2.getFitness()) {
                    return 0;
                }
                return i1.getFitness() > i2.getFitness() ? -1 : 1;
            });
            for (Individual individual : individuals) {
                if (individual.decode().validate()) {
                    // 选择第一个可行解
                    return individual;
                }
            }
            // 所有解都不可行, 严重bug
            throw new Exception("Error: solve bug occurs, please retry or contact LYL232");
        }
        return best;
    }

    /**
     * 检查各项参数是否配置正确
     *
     * @param parameter 参数
     * @throws ParameterException 参数异常
     */
    private void checkParameters(Parameter parameter) throws ParameterException {
        if (parameter.population < 1) {
            throw new ParameterException("GA.population must be greater than 0.");
        }
        if (parameter.maxGeneration < 1) {
            throw new ParameterException("GA.maxGeneration must be greater than 0.");
        }
        if (parameter.MSCrossoverRepeat < 1) {
            throw new ParameterException("GA.MSCrossoverRepeat must be greater than 0.");
        }
        if (parameter.workers < 1) {
            throw new ParameterException("GA.workers must be greater than 0.");
        }
        if (parameter.mutateRate < 0 || parameter.mutateRate > 1) {
            throw new ParameterException("GA.MSCrossoverRepeat must be in [0, 1].");
        }
        if (parameter.crossoverRate < 0 || parameter.crossoverRate > 1) {
            throw new ParameterException("GA.crossoverRate must be in [0, 1].");
        }
        if (parameter.selectBetterRate < 0 || parameter.selectBetterRate > 1) {
            throw new ParameterException("GA.selectBetterRate must be in [0, 1].");
        }
    }

    /**
     * 初始化工作
     */
    private void initialize() {
        // 初始化多线程
        if (parameter.workers > 1) {
            workers = new Worker[parameter.workers];
            for (int i = 0; i < workers.length; ++i) {
                workers[i] = new Worker(this);
                workers[i].setDaemon(true);
                workers[i].start();
            }
        }
        // 初始化种群
        for (int i = 0; i < parameter.population; ++i) {
            individuals[i] = newRandomIndividual();
        }
    }

    /**
     * 种群解码, 并维护统计信息
     */
    private void decodeIndividuals() throws Exception {
        fitnessSum = 0;
        if (parameter.workers > 1) {
            // 多线程
            assert workQueue.isEmpty();
            assert infoQueueII.isEmpty();
            assert infoQueueLII.isEmpty();
            queueLock.lock();
            responseRemain.set(workers.length);
            for (int j = 0; j < workers.length; ++j) {
                workQueue.add(WorkType.DECODE);
                infoQueueII.add(new Pair<>(j, workers.length));
            }
            queueCondition.signalAll();
            queueLock.unlock();
            waitResponse();
            assert workQueue.isEmpty();
            assert infoQueueII.isEmpty();
            assert infoQueueLII.isEmpty();
        } else {
            // 单线程
            for (Individual individual : individuals) {
                if (best.getFitness() < individual.getFitness()) {
                    best = individual;
                }
                fitnessSum += individual.getFitness();
            }
        }

        if (parameter.debug) {
            for (Individual individual : individuals) {
                Schedule schedule = individual.decode();
                if (!schedule.validate()) {
                    if (parameter.workers > 1) {
                        for (Worker worker : workers) {
                            worker.interrupt();
                        }
                    }
                    throw new Exception(String.format(
                            "Invalid individual generated:\nindividual:\n%s" +
                                    "errorMsg:\n%s\n",
                            individual, schedule.errorMsg()));
                }
            }
        }
    }

    /**
     * 等待任务回复
     *
     * @throws InterruptedException 中断异常
     */
    private void waitResponse() throws InterruptedException {
        synchronized (responseRemain) {
            // 等待任务回复
            while (responseRemain.get() > 0) {
                responseRemain.wait();
            }
        }
    }

    /**
     * 选择过程: 使用轮盘策略与精英策略结合: 保留适应度最大的1%,
     * 按锦标策略选择下一代, 开销较小, 访存频率高, 不适合多线程执行
     */
    private void selection(Random random) {
        // 种群大小, 直接保留的最佳个体数
        int n = individuals.length, keep = (int) ((double) n * 0.01);
        Arrays.sort(individuals, (i1, i2) -> {
            if (i1.getFitness() == i2.getFitness()) {
                return 0;
            }
            return i1.getFitness() > i2.getFitness() ? -1 : 1;
        });

        Individual[] newGeneration = new Individual[n];

        // 锦标赛策略
        // 直接保留最适应的1%
        for (int i = 0; i < keep; ++i) {
            // 保证每个个体都是一个独立的引用, 否则交叉选择等可能会影响到
            // 不同下标但是是同一个引用的个体
            newGeneration[i] = new Individual(individuals[i]);
        }
        for (int i = keep; i < n; ++i) {
            Individual a = individuals[random.nextInt(n - keep) + keep],
                    b = individuals[random.nextInt(n - keep) + keep];
            if (a.getFitness() < b.getFitness()) {
                // b更优
                if (random.nextDouble() < parameter.selectBetterRate) {
                    newGeneration[i] = new Individual(b);
                } else {
                    newGeneration[i] = new Individual(a);
                }
            } else {
                // a更优
                if (random.nextDouble() < parameter.selectBetterRate) {
                    newGeneration[i] = new Individual(a);
                } else {
                    newGeneration[i] = new Individual(b);
                }
            }
        }
        individuals = newGeneration;
    }

    /**
     * 交叉过程
     */
    private void crossover(Random random) throws Exception {
        if (parameter.workers > 1) {
            assert workQueue.isEmpty();
            assert infoQueueII.isEmpty();
            assert infoQueueLII.isEmpty();
            crossoverMultiThread(random);
            assert workQueue.isEmpty();
            assert infoQueueII.isEmpty();
            assert infoQueueLII.isEmpty();
        } else {
            crossoverSingleThread(random);
        }
    }

    /**
     * 交叉过程: 单线程
     */
    private void crossoverSingleThread(Random random) {
        Arrays.fill(crossovered, false);
        int n = individuals.length, keep = (int) ((double) n * 0.01);
        // 前1%的个体不参与交叉
        for (int i = keep; i < n; ++i) {
            if (crossovered[i]) {
                continue;
            }
            for (int j = i + 1; j < n; ++j) {
                if (crossovered[j]) {
                    continue;
                }
                if (random.nextDouble() >= parameter.crossoverRate) {
                    crossovered[i] = crossovered[j] = true;
                    Pair<Individual, Individual> childPair =
                            Individual.crossover(
                                    individuals[i], individuals[j],
                                    parameter.MSCrossoverRepeat, random
                            );
                    individuals[i] = childPair.getKey();
                    individuals[j] = childPair.getValue();
                    break;
                }
            }
        }
    }

    /**
     * 交叉过程: 多线程
     */
    private void crossoverMultiThread(Random random) throws Exception {
        assert workQueue.isEmpty();
        assert infoQueueII.isEmpty();
        assert infoQueueLII.isEmpty();
        Arrays.fill(crossovered, false);
        int n = individuals.length, keep = (int) ((double) n * 0.01);
        List<Pair<Integer, Integer>> workParam = new ArrayList<>();
        queueLock.lock();
        // 前1%的个体不参与交叉
        for (int i = keep; i < n; ++i) {
            if (crossovered[i]) {
                continue;
            }
            for (int j = i + 1; j < n; ++j) {
                if (crossovered[j]) {
                    continue;
                }
                if (random.nextDouble() >= parameter.crossoverRate) {
                    crossovered[i] = crossovered[j] = true;
                    workParam.add(new Pair<>(i, j));
                    if (workParam.size() >= 20) {
                        // 粗粒度分配: 一次分配20个
                        infoQueueLII.add(workParam);
                        workQueue.add(WorkType.CROSSOVER);
                        workParam = new ArrayList<>();
                    }
                    break;
                }
            }
        }
        if (!workParam.isEmpty()) {
            infoQueueLII.add(workParam);
            workQueue.add(WorkType.CROSSOVER);
        }
        responseRemain.set(infoQueueLII.size());
        queueCondition.signalAll();
        queueLock.unlock();
        waitResponse();
    }

    /**
     * 变异过程: 开销较小, 不适合多线程
     */
    private void mutate(Random random) {
        for (Individual individual : individuals) {
            if (random.nextDouble() < parameter.mutateRate / 2) {
                // MS段变异
                individual.MSMutate(random);
            }
            if (random.nextDouble() < parameter.mutateRate / 2) {
                // OS段变异
                individual.OSMutate(random);
            }
        }
    }

    /**
     * 根据这个环境的随机对象创建一个随机的个体
     *
     * @return 新个体
     */
    private Individual newRandomIndividual() {
        Random thisRandom = new Random(globalRandom.nextLong());
        int[] OS = new int[geneLength], MS = new int[geneLength];
        // MS基因段
        for (Piece piece : problem.pieces) {
            int begin = MSBegin[piece.id];
            List<Task> taskList = piece.taskList;
            for (int i = 0; i < taskList.size(); ++i) {
                MS[begin + i] = thisRandom.nextInt(
                        taskList.get(i).machineGroup.machines.size());
            }
        }
        // OS基因段
        System.arraycopy(OSGeneTemplate, 0, OS, 0, geneLength);
        // 打乱数组
        int temp, pos;
        for (int i = geneLength - 1; i > 0; --i) {
            pos = thisRandom.nextInt(i);
            temp = OS[pos];
            OS[pos] = OS[i];
            OS[i] = temp;
        }
        GeneChecker.getCurrentGeneChecker().fixOSGene(OS);
        return new Individual(this, MS, OS);
    }

    final public Problem problem;
    // 适应度计算器
    final public ScheduleEvaluator evaluator;

    // 包访问权限
    // 两段基因的长度
    final int geneLength;
    // 缓存变量: 每个工件的任务对应在MS段的起始位置
    final int[] MSBegin;
    // 缓存变量: 基因模板, 所有的可能的基因都是该模板的一个排列
    final int[] OSGeneTemplate;
    // MS基因段对应最大取值范围(不能取到)
    final int[] MSRange;
    // 最佳个体
    Individual best;
    // 种群的平均适应度
    double fitnessSum;

    // 多线程:
    // 发布任务队列
    final Lock queueLock = new ReentrantLock();
    final Condition queueCondition;
    // 参数队列: 两个整数
    final Queue<Pair<Integer, Integer>> infoQueueII = new LinkedList<>();
    // 参数队列: 两个整数组成的列表
    final Queue<List<Pair<Integer, Integer>>> infoQueueLII = new LinkedList<>();
    // 需要收到的同步回应
    final AtomicInteger responseRemain = new AtomicInteger(0);

    // 环境所拥有的个体
    Individual[] individuals;
    final Queue<WorkType> workQueue = new LinkedList<>();
    // 算法参数
    final Parameter parameter;
    // 全局随机对象
    final Random globalRandom;
    // 重用变量: 是否进行过交叉
    final private boolean[] crossovered;

    // 工作线程
    private Worker[] workers;

}
