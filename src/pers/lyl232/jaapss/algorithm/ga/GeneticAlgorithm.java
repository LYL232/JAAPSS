package pers.lyl232.jaapss.algorithm.ga;

import pers.lyl232.jaapss.algorithm.Solver;
import pers.lyl232.jaapss.problem.Problem;
import pers.lyl232.jaapss.problem.Schedule;

/**
 * 遗传算法
 */
public class GeneticAlgorithm implements Solver {

    /**
     * @param problem   问题描述对象
     * @param parameter 算法参数
     */
    public GeneticAlgorithm(Problem problem, Species.Parameter parameter)
            throws Exception {
        this.species = new Species(problem, parameter);
    }

    /**
     * 设置是否显示算法运行时信息
     *
     * @param verbose 是否要显示算法运行时信息
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public Schedule solve() throws Exception {
        return this.species.start(this.verbose).decode();
    }

    // 是否显示算法运行时信息
    private boolean verbose = false;
    // 种群对象
    final private Species species;
}
