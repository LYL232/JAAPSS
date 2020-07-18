package pers.lyl232.jaapss.program;

import pers.lyl232.jaapss.algorithm.Solver;
import pers.lyl232.jaapss.algorithm.ga.GeneticAlgorithm;
import pers.lyl232.jaapss.algorithm.ga.Species;
import pers.lyl232.jaapss.data.loader.CSVMachineLoader;
import pers.lyl232.jaapss.data.loader.CSVTaskLoader;
import pers.lyl232.jaapss.data.loader.DataLoadException;
import pers.lyl232.jaapss.problem.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface Command {
    /**
     * 执行命令并返回结果
     *
     * @param args 命令参数, 长度至少为1(无参数), args[0]等于该命令的名字
     * @return 需要输出的值, 如不需输出返回null
     */
    String execute(String[] args);
}

/**
 * help命令: help(void)
 * return help 信息
 */
class HelpCommand implements Command {
    @Override
    public String execute(String[] args) {
        return "Available Commands:\n" +
                "exit: exit the program.\n" +
                "start: start solving the problem.\n" +
                "set [parameter name] [value]...: set program parameter value.\n" +
                "SP as showParameters: show program parameter values.\n" +
                "\nGeneral Parameters:\n" +
                "taskCSV: task info csv data file, default: ./data/example-task.csv\n" +
                "machineCSV: machine info csv data file, default: ./data/example-machine.csv\n" +
                "hasHeader: whether data csv files have header, default: true\n" +
                "outputCSV: output csv file, default: ./data/output.csv\n" +
                "encoding: input output file encoding, default: GBK\n" +
                "verbose: whether show runtime information, default: false\n" +
                "workHours: work hours per day, default: 8:00-24:00, example: 'set workHours 8 0 24 0'\n" +
                "timeunit: task timeunit, default: minute, optional: ['ms', 's', 'm', 'h', 'd']\n" +
                "SR as scheduleRule: default: 'FORWARD', optional:['0' or 'FORWARD', '1' or 'BACKWARD']\n" +
                "SS as scheduleStrategy: default: 'LEAST_EXCEED_TIME', " +
                "optional:['0' or 'LEAST_EXCEED_TIME', '1' or 'LEAST_EXPIRED_TASK'," +
                " '2' : 'HIGHEST_MACHINE_UTILIZATION']\n" +
                "algorithm: default: 'GA', optional:['GA': Genetic Algorithm]\n" +
                "VMG as virtualMachineGroups: virtual machine group id set, default: {}," +
                " example: 'set virtualMachineGroups 58 59'\n" +
                "\nGA Algorithm Parameters:\n" +
                "GA.population: default: 200\n" +
                "GA.maxGeneration: default: 100\n" +
                "GA.MSCrossoverRepeat: default: 10\n" +
                "GA.mutateRate: default: 0.01\n" +
                "GA.crossoverRate: default: 0.6\n" +
                "GA.selectBetterRate: default: 0.8\n" +
                "GA.seed: default: current timestamp\n" +
                "GA.workers: default: available cpus";
    }
}

/**
 * 设置参数命令: set(variableName, value)
 * return null(正常) 或者 异常信息(不正常)
 */
class SetCommand implements Command {

    SetCommand(Program program) {
        this.program = program;
    }

    @Override
    public String execute(String[] args) {
        if (args.length < 3) {
            return "Error: invalid set command: " +
                    "set commands receive at least two arguments.";
        }
        try {
            switch (args[1]) {
                case "taskCSV": {
                    program.taskCSV = args[2];
                    break;
                }
                case "machineCSV": {
                    program.machineCSV = args[2];
                    break;
                }
                case "outputCSV": {
                    program.outputCSV = args[2];
                    break;
                }
                case "hasHeader": {
                    program.hasHeader = Boolean.parseBoolean(args[2]);
                    break;
                }
                case "encoding": {
                    program.encoding = args[2];
                    break;
                }
                case "verbose": {
                    program.verbose = Boolean.parseBoolean(args[2]);
                    break;
                }
                case "workHours": {
                    if (args.length < 6) {
                        return "set workHour receive at least 4 arguments " +
                                "for example[8:00-24:00): 8 0 24 0";
                    }
                    program.workHours = new DayHourMinute(
                            Integer.parseInt(args[2]),
                            Integer.parseInt(args[3]),
                            Integer.parseInt(args[4]),
                            Integer.parseInt(args[5])
                    );
                    break;
                }
                case "timeunit": {
                    switch (args[2]) {
                        case "ms": {
                            program.timeunit = TimeUnit.MILLISECOND;
                            break;
                        }
                        case "s": {
                            program.timeunit = TimeUnit.SECOND;
                            break;
                        }
                        case "m": {
                            program.timeunit = TimeUnit.MINUTE;
                            break;
                        }
                        case "h": {
                            program.timeunit = TimeUnit.HOUR;
                            break;
                        }
                        case "d": {
                            program.timeunit = TimeUnit.DAY;
                            break;
                        }
                        default: {
                            return String.format("Error: unknown timeunit: %s",
                                    args[2]);
                        }
                    }
                    break;
                }
                case "algorithm": {
                    if (!args[2].equals("GA")) {
                        return String.format("invalid parameter algorithm: %s, " +
                                "optional: ['GA']", args[2]);
                    }
                    program.algorithm = args[2];
                    break;
                }
                case "VMG":
                case "virtualMachineGroups": {
                    Set<Integer> virtualMachineGroups = new HashSet<>();
                    for (int i = 2; i < args.length; ++i) {
                        virtualMachineGroups.add(Integer.parseInt(args[i]));
                    }
                    program.virtualMachineGroups = virtualMachineGroups;
                    break;
                }
                case "SS":
                case "scheduleStrategy": {
                    switch (args[2]) {
                        case "0":
                        case "LEAST_EXCEED_TIME": {
                            program.scheduleStrategy = ScheduleStrategy.LEAST_EXCEED_TIME;
                            break;
                        }
                        case "1":
                        case "LEAST_EXPIRED_TASK": {
                            program.scheduleStrategy = ScheduleStrategy.LEAST_EXPIRED_TASK;
                            break;
                        }
                        case "2":
                        case "HIGHEST_MACHINE_UTILIZATION": {
                            program.scheduleStrategy = ScheduleStrategy.HIGHEST_MACHINE_UTILIZATION;
                            break;
                        }
                        default: {
                            return String.format("Error: unknown ScheduleStrategy: %s",
                                    args[2]);
                        }
                    }
                    break;
                }
                case "SR":
                case "scheduleRule": {
                    switch (args[2]) {
                        case "0":
                        case "FORWARD": {
                            program.scheduleRule = ScheduleRule.FORWARD;
                            break;
                        }
                        case "1":
                        case "BACKWARD": {
                            program.scheduleRule = ScheduleRule.BACKWARD;
                            break;
                        }
                        default: {
                            return String.format("Error: unknown ScheduleRule: %s",
                                    args[2]);
                        }
                    }
                    break;
                }
                case "GA.population": {
                    program.GAParameter.population = Integer.parseInt(args[2]);
                    break;
                }
                case "GA.maxGeneration": {
                    program.GAParameter.maxGeneration = Integer.parseInt(args[2]);
                    break;
                }
                case "GA.MSCrossoverRepeat": {
                    program.GAParameter.MSCrossoverRepeat = Integer.parseInt(args[2]);
                    break;
                }
                case "GA.mutateRate": {
                    program.GAParameter.mutateRate = Double.parseDouble(args[2]);
                    break;
                }
                case "GA.crossoverRate": {
                    program.GAParameter.crossoverRate = Double.parseDouble(args[2]);
                    break;
                }
                case "GA.selectBetterRate": {
                    program.GAParameter.selectBetterRate = Double.parseDouble(args[2]);
                    break;
                }
                case "GA.seed": {
                    program.GAParameter.seed = Integer.parseInt(args[2]);
                    break;
                }
                case "GA.workers": {
                    program.GAParameter.workers = Integer.parseInt(args[2]);
                    break;
                }
                case "OF":
                case "outputFormat": {
                    program.outputFormat = Integer.parseInt(args[2]);
                    break;
                }
                default: {
                    return String.format("Error: unknown parameter: %s",
                            args[1]);
                }
            }
        } catch (Exception exception) {
            return String.format("Error: invalid set command. message: %s," +
                            "type 'help' for instruction.",
                    exception.getMessage());
        }
        return null;
    }

    final private Program program;
}

/**
 * 显示当前程序的参数命令
 * return 参数
 */
class ShowParametersCommand implements Command {

    ShowParametersCommand(Program program) {
        this.program = program;
    }

    @Override
    public String execute(String[] args) {
        StringBuilder builder = new StringBuilder("General Parameters:\n");
        builder.append(String.format("taskCSV: %s\n", program.taskCSV));
        builder.append(String.format("machineCSV: %s\n", program.machineCSV));
        builder.append(String.format("hasHeader: %s\n", program.hasHeader));
        builder.append(String.format("outputCSV: %s\n", program.outputCSV));
        builder.append(String.format("encoding: %s\n", program.encoding));
        builder.append(String.format("verbose: %s\n", program.verbose));
        builder.append(String.format("workHours: %s\n", program.workHours));
        builder.append(String.format("timeunit: %s\n", program.timeunit));
        builder.append(String.format("verbose: %s\n", program.verbose));
        builder.append(String.format("SR as scheduleRule: %s\n", program.scheduleRule));
        builder.append(String.format("SS as scheduleStrategy: %s\n", program.scheduleStrategy));
        builder.append(String.format("algorithm: %s\n", program.algorithm));
        builder.append(String.format("VMG as virtualMachineGroups: %s\n", program.virtualMachineGroups));

        if (program.algorithm.equals("GA")) {
            builder.append("\nGA Algorithm Parameters:\n");
            builder.append(String.format("GA.population: %s\n", program.GAParameter.population));
            builder.append(String.format("GA.maxGeneration: %s\n", program.GAParameter.maxGeneration));
            builder.append(String.format("GA.MSCrossoverRepeat: %s\n", program.GAParameter.MSCrossoverRepeat));
            builder.append(String.format("GA.mutateRate: %s\n", program.GAParameter.mutateRate));
            builder.append(String.format("GA.crossoverRate: %s\n", program.GAParameter.crossoverRate));
            builder.append(String.format("GA.selectBetterRate: %s\n", program.GAParameter.selectBetterRate));
            builder.append(String.format("GA.seed: %s\n", program.GAParameter.seed));
            builder.append(String.format("GA.workers: %s", program.GAParameter.workers));
        }

        return builder.toString();
    }

    final private Program program;
}


/**
 * 启动命令: start(void)
 * return null(正常) 或者 异常信息(不正常)
 */
class StartCommand implements Command {
    StartCommand(Program program) {
        this.program = program;
    }

    @Override
    public String execute(String[] args) {
        try {
            Problem problem = new Problem(
                    new CSVMachineLoader(program.machineCSV, program.hasHeader, program.encoding),
                    new CSVTaskLoader(program.taskCSV, program.hasHeader, program.encoding),
                    program.workHours, program.timeunit);
            // 注册虚拟设备组
            problem.virtualMachineGroups.clear();
            problem.virtualMachineGroups.addAll(program.virtualMachineGroups);

            Solver solver;
            if ("GA".equals(program.algorithm)) {
                Species.Parameter parameter = program.GAParameter;
                parameter.scheduleRule = program.scheduleRule;
                parameter.scheduleStrategy = program.scheduleStrategy;
                GeneticAlgorithm ga = new GeneticAlgorithm(problem, parameter);
                ga.setVerbose(program.verbose);
                solver = ga;
            } else {
                return String.format("Error: unknown algorithm %s",
                        program.algorithm);
            }
            Schedule result = solver.solve();
            if (program.verbose) {
                System.out.println("result:");
                System.out.println(result);
                printScheduleMetrics(result);
                System.out.println("outputting...");
            }
            // 输出
            if (program.outputFormat == 1) {
                result.toCSVInternal(program.outputCSV, program.encoding);
            } else {
                result.toCSV(program.outputCSV, program.encoding);
            }

        } catch (IOException | DataConsistencyException | DataLoadException exception) {
            return String.format("Solve Error: %s", exception.getMessage());
        } catch (Exception exception) {
            exception.printStackTrace();
            return String.format("Solve Error: unknown exception %s",
                    exception.getMessage());
        }
        return null;
    }

    /**
     * 打印出调度方案的各项指标
     *
     * @param schedule 调度方案
     */
    static private void printScheduleMetrics(Schedule schedule) {
        // 方案总共执行时间
        double timeCost = 0;
        // 超时的分配列表
        List<Assignment> expireAssignments = new ArrayList<>();
        for (Assignment assignment : schedule.assignments) {
            Task task = assignment.task;
            timeCost = Math.max(timeCost, assignment.getEndAt());
            if (task.expireTime > 0 && assignment.getEndAt() - task.expireTime > 1e-5) {
                expireAssignments.add(assignment);
            }
        }
        System.out.printf("schedule time cost: %.2f\n", timeCost);
        if (expireAssignments.size() > 0) {
            System.out.println("expire tasks:");
            for (Assignment assignment : expireAssignments) {
                System.out.printf("Task-%d: exceed: %.2f\n", assignment.task.id,
                        assignment.getEndAt() - assignment.task.expireTime);
            }
        }
    }

    final private Program program;
}
