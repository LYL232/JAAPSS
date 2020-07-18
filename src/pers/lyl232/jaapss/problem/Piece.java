package pers.lyl232.jaapss.problem;

import java.util.Collections;
import java.util.List;

/**
 * 工件类, 逻辑概念, 将一串有序的任务归为一个虚拟工件所需要的一串任务
 */
public class Piece {

    /**
     * @return 后继工件
     */
    public Piece getSuccessor() {
        return successor;
    }

    /**
     * 注册生成一个工件
     *
     * @param id          工件序号: 从0连续开始
     * @param taskList    工件对应的有序任务列表
     * @param predecessor 前驱工件列表
     */
    Piece(int id, List<Task> taskList, List<Piece> predecessor) {
        this.id = id;
        this.taskList = Collections.unmodifiableList(taskList);
        this.predecessor = predecessor;
    }

    @Override
    public String toString() {
        StringBuilder taskListBuilder = new StringBuilder("["),
                prePiecesBuilder = new StringBuilder("[");
        for (int i = 0; i < taskList.size(); ++i) {
            taskListBuilder.append(taskList.get(i).id);
            if (i < taskList.size() - 1) {
                taskListBuilder.append(',');
            }
        }
        taskListBuilder.append(']');
        for (int i = 0; i < predecessor.size(); ++i) {
            prePiecesBuilder.append(predecessor.get(i).id);
            if (i < predecessor.size() - 1) {
                prePiecesBuilder.append(',');
            }
        }
        prePiecesBuilder.append(']');
        return String.format("Piece{id: %d, taskList: %s, predecessor: %s}",
                id, taskListBuilder.toString(), prePiecesBuilder.toString());
    }

    final public Integer id;
    // 工件对应的任务列表
    final public List<Task> taskList;
    // 工件的前驱工件
    final public List<Piece> predecessor;
    // 工件的后继工件, 由于创建该对象时该信息未知, 所以需要包访问权限由知道全局信息的类进行维护
    Piece successor = null;
}
