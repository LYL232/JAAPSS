package pers.lyl232.jaapss.program;

import pers.lyl232.jaapss.algorithm.ga.Species;
import pers.lyl232.jaapss.problem.DayHourMinute;
import pers.lyl232.jaapss.problem.ScheduleRule;
import pers.lyl232.jaapss.problem.ScheduleStrategy;
import pers.lyl232.jaapss.problem.TimeUnit;

import java.util.*;

public class Program {

    public static Program getInstance() {
        if (program == null) {
            program = new Program();
            commandMap.put("help", new HelpCommand());
            commandMap.put("start", new StartCommand(program));
            commandMap.put("set", new SetCommand(program));
            commandMap.put("showParameters", new ShowParametersCommand(program));
            commandMap.put("SP", new ShowParametersCommand(program));
        }
        return program;
    }

    /**
     * 启动程序
     */
    public void run() {
        Scanner scanner = new Scanner(System.in, "UTF-8");
        try {
            while (true) {
                System.out.print(":)");
                String input = scanner.nextLine().trim();
                String[] parts = input.split("\\s+");
                if (parts[0].equals("exit")) {
                    return;
                } else if(parts[0].equals("")) {
                    continue;
                }

                Command command = commandMap.get(parts[0]);
                if (command == null) {
                    System.out.printf("unknown command: %s\n", parts[0]);
                    continue;
                }
                String report = command.execute(parts);
                if (report != null && !report.equals("")) {
                    System.out.println(report);
                }
            }
        } catch (Exception exception) {
            System.out.printf("unknown exception: %s\n", exception.getMessage());
            System.exit(1);
        }
    }

    // 输入:
    // 任务信息csv文件路径
    String taskCSV = "./data/example-task.csv";
    // 设备信息csv文件路径
    String machineCSV = "./data/example-machine.csv";
    // 输出:
    String outputCSV = "./data/output.csv";
    // 配置:
    // csv文件是否有表头
    boolean hasHeader = true;
    // 编码集
    String encoding = "GBK";
    // 是否输出运行时信息
    boolean verbose = false;
    // 排程规则: 正排, 倒排
    ScheduleRule scheduleRule = ScheduleRule.FORWARD;
    // 排程目标: 最少超时时间, 最少超时任务, 最大设备利用率
    ScheduleStrategy scheduleStrategy = ScheduleStrategy.LEAST_EXCEED_TIME;
    // 工作时间: 默认8:00-24:00
    DayHourMinute workHours = new DayHourMinute(8, 0, 24, 0);
    // 时间单位: 默认分钟
    TimeUnit timeunit = TimeUnit.MINUTE;
    // 0是默认的输出格式, 1是内部信息的输出格式(debug用, 所以该字段并不会出现在help中)
    int outputFormat = 0;


    // 选择的算法
    String algorithm = "GA";

    // 遗传算法运行参数
    final Species.Parameter GAParameter = new Species.Parameter();

    // 虚拟设备组id集合: 分配到这些组的任务可以直接开始不必等待空余设备
    Set<Integer> virtualMachineGroups = new HashSet<>();

    // 单例
    static private Program program = null;
    // 命令名对应的命令
    final static private Map<String, Command> commandMap = new HashMap<>();

}
