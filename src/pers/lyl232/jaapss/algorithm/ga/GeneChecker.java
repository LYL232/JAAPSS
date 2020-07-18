package pers.lyl232.jaapss.algorithm.ga;

import pers.lyl232.jaapss.problem.Piece;
import pers.lyl232.jaapss.problem.Problem;

import java.util.*;

/**
 * 基因校验器, 验证基因有效性并纠错, 考虑到执行的操作比较频繁, 其实现并不是线程安全的
 */
public class GeneChecker {

    /**
     * 获取当前线程对应的检测器
     *
     * @return 检测器
     */
    static GeneChecker getCurrentGeneChecker() {
        return geneCheckerMap.get(Thread.currentThread().getId());
    }

    /**
     * 给当前线程新分配一个基因检测器
     *
     * @param problem 问题引用
     */
    static void initializeCurrentGeneChecker(Problem problem) {
        if (geneCheckerMap.get(Thread.currentThread().getId()) == null) {
            geneCheckerMap.put(Thread.currentThread().getId(), new GeneChecker(problem));
        }
    }

    /**
     * 当一个线程对象被销毁前, 调用这个方法释放专属这个线程的基因检测器
     */
    static void removeCurrentGeneChecker() {
        geneCheckerMap.remove(Thread.currentThread().getId());
    }

    /**
     * 校验并修正OS基因段, 使得工件间的依赖得以保持
     *
     * @param geneSeq 需要校验和修正的OS基因段
     */
    public void fixOSGene(int[] geneSeq) {
        Arrays.fill(queue, 0);
        System.arraycopy(problem.piecesDependencyCount, 0, remainDependencyCount, 0, remainDependencyCount.length);

        // 重排时第一个不确定是否正确的位置
        int reIndex = 0;
        // 扫描基因错误位置, 将不符合拓扑排序的位置记录到错误队列中
        for (int i = 0; i < geneSeq.length; ++i) {
            int pId = geneSeq[i];
            // 检测当前工件是否仍有依赖
            if (remainDependencyCount[pId] > 0) {
                // 加入重排队列中
                ++queue[pId];
                continue;
            }
            // 按原顺序重排数组
            geneSeq[reIndex++] = geneSeq[i];
            // 后继工件所需依赖-1
            Piece suc = problem.piecesSuc[pId];
            if (suc != null) {
                --(remainDependencyCount[suc.id]);
                // 类似拓扑排序
                // 重排在重排队列中的工件: 从suc开始: 如果其本身, 后继都满足:
                // 依赖为0(都已完成) 且 在重排队列中有非0计数
                while (remainDependencyCount[suc.id] == 0 && queue[suc.id] > 0) {
                    pId = suc.id;
                    int count = queue[pId];
                    // 连续将队列中的所有pId都重排列到已排好的基因上
                    while (queue[pId] > 0) {
                        geneSeq[reIndex++] = pId;
                        --queue[pId];
                    }
                    suc = suc.getSuccessor();
                    if (suc == null) {
                        break;
                    }
                    remainDependencyCount[suc.id] -= count;
                }
            }
        }
    }

    /**
     * 根据问题的规模初始化所需资源
     *
     * @param problem 问题
     */
    private GeneChecker(Problem problem) {
        this.problem = problem;
        remainDependencyCount = new int[problem.pieces.size()];
        queue = new int[problem.pieces.size()];
    }

    // 所属问题的引用
    final private Problem problem;

    // 重用变量: 每个工件的剩余前驱个数
    final private int[] remainDependencyCount;
    // 重用变量: 每个工件在重排队列中的个数
    final private int[] queue;
    // 为了防止多线程访问检测器冲突, 并且能够在任意地方访问到基因检测器,
    // 给每个线程分配一个检测器, 维护线程id->对应的检测器
    final private static Map<Long, GeneChecker> geneCheckerMap = new HashMap<>();
}
