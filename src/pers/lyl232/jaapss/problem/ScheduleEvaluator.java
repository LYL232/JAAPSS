package pers.lyl232.jaapss.problem;

/**
 * 调度评估类: 用于获取遗传算法适应度等
 */
public abstract class ScheduleEvaluator {

    protected ScheduleEvaluator(Problem problem, ScheduleRule rule) {
        this.problem = problem;
        this.rule = rule;
    }

    /**
     * 根据调度评估配置计算调度的得分
     *
     * @param schedule 调度得分
     * @return 适应度
     */
    public abstract double evaluate(Schedule schedule);

    /**
     * 最优化指标(提前终止条件)
     *
     * @return 是否最优化(已经达标)
     */
    public abstract double getOptimized();

    /**
     * 根据排程策略生成调度评估对象
     *
     * @param scheduleStrategy 排程策略
     * @return 适应度计算器
     */
    public static ScheduleEvaluator get(
            Problem problem, ScheduleStrategy scheduleStrategy,
            ScheduleRule rule) {
        switch (scheduleStrategy) {
            case LEAST_EXCEED_TIME:
                return new LeastExceedTimeFitness(problem, rule);
            case LEAST_EXPIRED_TASK:
                return new LeastExpiredTaskFitness(problem, rule);
            case HIGHEST_MACHINE_UTILIZATION:
                return new HighestMachineUtilizationFitness(problem, rule);
        }
        return null;
    }

    // 保留问题引用
    final protected Problem problem;
    // 排程规则
    final public ScheduleRule rule;
}

/**
 * 每个任务最少超时策略: 要求超时总和越小越好, 且超时的量越平均越好, 简化为
 * 适应度 = -1 * (所有任务超时总和 + 超时任务的超时时间的标准差)
 */
class LeastExceedTimeFitness extends ScheduleEvaluator {

    LeastExceedTimeFitness(Problem problem, ScheduleRule rule) {
        super(problem, rule);
    }

    @Override
    public double evaluate(Schedule schedule) {
        double sum = 0, std = 0;
        int expireTasks = 0;
        for (Assignment assignment : schedule.assignments) {
            Task task = assignment.task;
            if (task.expireTime >= 0) {
                double exceed = assignment.getEndAt() - task.expireTime;
                if (exceed > 1e-5) {
                    sum -= exceed;
                    std += exceed * exceed;
                    ++expireTasks;
                }
            }
        }
        std = Math.sqrt(std);
        if (expireTasks > 1) {
            std /= (expireTasks - 1);
        }
        // std是正数, sum是负数
        return sum - std;
    }

    @Override
    public double getOptimized() {
        return 0;
    }
}

/**
 * 最少超时任务策略
 */
class LeastExpiredTaskFitness extends ScheduleEvaluator {

    LeastExpiredTaskFitness(Problem problem, ScheduleRule rule) {
        super(problem, rule);
    }

    @Override
    public double evaluate(Schedule schedule) {
        double result = 0;
        for (Assignment assignment : schedule.assignments) {
            Task task = assignment.task;
            if (task.expireTime >= 0 &&
                    assignment.getEndAt() - task.expireTime > 1e-5) {
                result -= 1;
            }
        }
        return result;
    }

    @Override
    public double getOptimized() {
        return 0;
    }
}

/**
 * 最高设备利用率策略
 */
class HighestMachineUtilizationFitness extends ScheduleEvaluator {

    HighestMachineUtilizationFitness(Problem problem, ScheduleRule rule) {
        super(problem, rule);
    }

    @Override
    public double evaluate(Schedule schedule) {
        // 这里的设备利用率是指每个设备的使用时间与整个调度的时长之比的和?, 如果是这样
        // 所有设备的使用时间一定(由任务决定) 那么调度时长越小, 设备利用率越高, 直接返回
        // 时长即可(取负数)
        double res = 0;
        for (Assignment assignment : schedule.assignments) {
            res = Math.min(res, -1 * assignment.getEndAt());
        }
        return res;
    }

    @Override
    public double getOptimized() {
        return 0;
    }

}
