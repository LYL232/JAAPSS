# 只是一个APS问题求解器

## 求解问题描述

现有一批任务和一批设备. 除了最终的任务, 每个任务都有一个后继任务, 在当前任务未完成前, 后继任务不可开始. 每个任务都需要一段给定的连续的工作时间(当天工作结束时间可以看做与第二天工作开始时间连续). 设备分为设备组, 设备组里每个设备可以看做相同, 每个任务分配生产时都需要指定某个设备组里的一个设备进行生产. 同一时间同一个设备不可以执行两个任务. 给定某些任务的计划完成时间.

求: 在不同排程策略, 不同排程规则下的生产计划.

目前程序提供的排程策略: 

1. 最少超时时间总和且每个设备超时的时间越少越好 (最少超时时间)
2. 最少超时任务数
3. 最高设备利用率(由于每个任务的所需完成时间一定, 所以等价于最短工时)

目前程序提供的排程规则: 正排, 倒排

## 使用方式

该程序是一个命令行交互程序, 计算输入输出和算法参数配置从标准输入中读出的用户命令控制.

当程序准备就绪需要读入用户命令时: 屏幕上会输出一行":)".

### 基础使用方式:

设当前工作目录在项目根目录下

```sh
java -jar JAAPSS.jar
```

运行程序命令行

依次在标准输入中输入

```
set taskCSV ./data/example-task.csv
set machineCSV ./data/example-machine.csv
set outputCSV ./data/output.csv
start
exit
```

当start命令执行结束且输出了":)"时即可得到输出结果: ./data/output.csv

### 命令:

- help: 输出程序使用指南和参数名
- set [参数名] [参数值...]: 设置程序运行时参数
- showParameters 或 SP: 显示当前程序运行参数
- start: 按照程序当前运行参数求解问题
- exit: 退出程序

### 输入:

两个csv文件: 由程序运行时参数 taskCSV 和 machineCSV 指出其路径

- taskCSV: 任务信息csv文件, 格式可参见 data/example-task.csv, 其中任务所需时间 = 准备时间 + 任务数量 $\times$ 运行时间
- machineCS V: 设备信息csv文件 格式可参见 data/example-machine.csv

### 输出:

一个csv文件: 由程序运行时参数outputCSV指出其路径

### 运行时参数:

#### 通用参数

- taskCSV: 输入任务信息csv文件, 默认: ./data/example-task.csv
- machineCSV: 输入设备信息csv文件, 默认: ./data/example-machine.csv
- hasHeader: 输入csv文件是否有表头, 默认: true
- outputCSV: 输出csv文件, 默认: ./data/output.csv
- encoding: 输入输出编码, 默认: GBK
- verbose: 是否显示算法运行时信息和调度访问信息, 默认: false
- workHours: 每天的工作时间, 默认: 8:00-24:00, 示例: 'set workHours 8 0 24 0'
- timeunit: 任务中时间的时间单位, 默认: minute (分), 可选: ['ms', 's', 'm', 'h', 'd']
- SR as scheduleRule: 排程规则 默认: 'FORWARD' (正排), 可选:['0' 或者 'FORWARD', '1' 或者 'BACKWARD']
- SS as scheduleStrategy: 排程策略 默认: 'LEAST_EXCEED_TIME' (最少超时时间), 可选 :['0' 或者 'LEAST_EXCEED_TIME', '1' 或者 'LEAST_EXPIRED_TASK' (最少超时任务数) , '2'  或者 'HIGHEST_MACHINE_UTILIZATION' (最大设备利用率)]
- algorithm: 求解算法 默认: 'GA', 可选 :['GA': Genetic Algorithm (遗传算法)]
- VMG as virtualMachineGroups: 虚拟设备组, 该设备组内的设备可以无限并行执行任务, 默认: 空集, 示例: 'set virtualMachineGroups 58 59'

#### 遗传算法参数:

- GA.population: 种群大小, 默认: 200
- GA.maxGeneration: 最大迭代次数, 默认: 100
- GA.MSCrossoverRepeat: MS基因段尝试交叉个数, 默认: 10
- GA.mutateRate: 个体变异概率, 默认: 0.01
- GA.crossoverRate: 个体交叉概率, 默认: 0.6
- GA.selectBetterRate: 二选一锦标赛选择策略中选择较强个体的概率. 默认: 0.8
- GA.seed: 随机种子, 默认:当前时间戳
- GA.workers: 算法工作线程个数, 默认: 当前可用处理器个数

## 实现简述

### 遗传算法

注意到问题与FJSSP(Flexible Job Shop Scheduling Problem 柔性作业调度问题)相似, 所以借鉴了[mnmalist](https://blog.csdn.net/dfb198998)的[博客](https://blog.csdn.net/mnmlist/article/details/79056883)和[柔性作业车间调度问题的两级遗传算法](http://www.cnki.com.cn/Article/CJFDTotal-JXXB200704020.htm)的基于MS(机器选择段)和OS(工序选择段)的编码方式对遗传算法个体进行编码解码.

但是本问题与FJSSP的仍有不同点:

1. 本问题中没有工件的概念, 任务与FJSSP中的工序概念不能直接对应, 因为FJSSP中的工件包含了一连串的工序, 这些工序需要严格按顺序执行. 而本问题中的任务间依赖是一颗多叉树(因为一个任务只有一个后继任务), 不能直接将任务转换为工序.
2. 本问题中所有任务的执行所需时间与设备无关.

使用MS,OS编码方式的关键是解决不同点1.

解决方式如下:

注意到一个任务只有一个后继任务, 所以从一个最终任务开始搜索前驱任务能够形成一棵依赖多叉树. 可以将这棵依赖树分解成若干连续的依赖链条, 举例如下:

```
1<-2<-3
       \
     4<-7<-8<-9
       /
   5<-6
```

任务9是最终需要完成的任务, 箭头指向前驱任务, 注意到1<-2<-3是一个严格顺序的任务链, 那么可以把[1<-2<-3]看做一个工件的工序链, 即把1, 2, 3看做一个虚拟的工件的工序, 工件编号为1, 那么依赖树变成如下的工件依赖

```
[1<-2<-3]                 {1}
         \                   \
     [4]<-[7<-8<-9] =>  {2}<-{4}
         /                   /
   [5<-6]                 {3}
```

那么OS基因段长度为9, 包含3个1, 1个2, 2个3, 3个4, 只需保证4的右边不会出现1, 2, 3就能保证该OS基因是一个可行解的编码. 每次生成一个OS基因段时, 只需使用一个检错纠错算法将其修正成满足依赖条件OS基因序列即可.  本程序借鉴了拓扑排序实现了O(n)的检错纠错算法. 实现了本问题到FJSSP的转化.

至此, 按照FJSSP的遗传算法求解转化完的问题即可.

#### 参考文献:

https://blog.csdn.net/mnmlist/article/details/79056883

http://www.cnki.com.cn/Article/CJFDTotal-JXXB200704020.htm