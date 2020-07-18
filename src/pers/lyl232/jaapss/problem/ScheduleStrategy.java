package pers.lyl232.jaapss.problem;

/**
 * 排程策略:
 */
public enum ScheduleStrategy {
    LEAST_EXCEED_TIME, // 最少逾期时间
    LEAST_EXPIRED_TASK, // 最少逾期任务
    HIGHEST_MACHINE_UTILIZATION // 最多设备利用率
}
