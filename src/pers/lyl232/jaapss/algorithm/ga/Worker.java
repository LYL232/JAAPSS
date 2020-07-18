package pers.lyl232.jaapss.algorithm.ga;

import javafx.util.Pair;

import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 工作类型, 告诉每个线程该做什么类型的任务
 */
enum WorkType {
    DECODE, // 解码新一代的种群个体
    CROSSOVER, // 交叉操作
    EXIT, // 退出
}

/**
 * 多线程加速遗传算法, 接受来自Species对象发布的任务并执行
 */
public class Worker extends Thread {
    public Worker(Species species) {
        this.species = species;
    }

    @Override
    public void run() {
        // 初始化非线程安全类: 基因检测器
        GeneChecker.initializeCurrentGeneChecker(species.problem);
        Queue<WorkType> workQueue = species.workQueue;
        Lock queueLock = species.queueLock;
        Condition queueCondition = species.queueCondition;
        try {
            while (true) {
                queueLock.lock();
                while (workQueue.isEmpty()) {
                    queueCondition.await();
                }
                switch (workQueue.poll()) {
                    case DECODE: {
                        decode();
                        break;
                    }
                    case CROSSOVER: {
                        crossover();
                        break;
                    }
                    case EXIT: {
                        queueLock.unlock();
                        return;
                    }
                }
            }
        } catch (Exception exception) {
            System.out.printf("GA.Worker-%d:Exception occurs: %s\n",
                    Thread.currentThread().getId(), exception.getMessage());
            exception.printStackTrace();
        }
        // 释放这个线程专用的基因检测器
        GeneChecker.removeCurrentGeneChecker();
    }

    /**
     * 交叉操作
     */
    private void crossover() {
        // 粗粒度分配
        List<Pair<Integer, Integer>> workList = species.infoQueueLII.poll();
        assert workList != null;
        Random random = new Random(species.globalRandom.nextLong());
        species.queueLock.unlock();
        for (Pair<Integer, Integer> info : workList) {
            int i = info.getKey(), j = info.getValue();
            Individual[] individuals = species.individuals;
            Pair<Individual, Individual> childPair = Individual.crossover(
                    individuals[i], individuals[j],
                    species.parameter.MSCrossoverRepeat, random
            );
            individuals[i] = childPair.getKey();
            individuals[j] = childPair.getValue();
        }

        // 向主线程回复任务已完成
        synchronized (species.responseRemain) {
            if (species.responseRemain.decrementAndGet() == 0) {
                species.responseRemain.notifyAll();
            }
        }

    }

    /**
     * 新一代个体解码
     */
    private void decode() {
        Pair<Integer, Integer> info = species.infoQueueII.poll();
        assert info != null;
        species.queueLock.unlock();
        Individual[] individuals = species.individuals;
        Individual best = individuals[0];
        double fitnessSum = 0;
        // 解码并更新种群全局数据
        for (int i = info.getKey(); i < individuals.length; i += info.getValue()) {
            if (best.getFitness() < individuals[i].getFitness()) {
                best = individuals[i];
            }
            fitnessSum += individuals[i].getFitness();
        }


        synchronized (species.responseRemain) {
            if (species.responseRemain.decrementAndGet() == 0) {
                species.responseRemain.notifyAll();
            }
            if (species.best.getFitness() < best.getFitness()) {
                species.best = best;
            }
            species.fitnessSum += fitnessSum;
        }

    }

    final private Species species;
}
