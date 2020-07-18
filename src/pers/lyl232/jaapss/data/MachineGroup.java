package pers.lyl232.jaapss.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 设备组: 描述每个设备组所拥有的设备个数
 */
public class MachineGroup {

    /**
     * @param id       组id
     * @param machines 组内所有设备id
     */
    public MachineGroup(int id, List<Integer> machines) {
        this.id = id;
        this.machines = machines;
        this.machineSet = Collections.unmodifiableSet(new HashSet<>(machines));
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < machines.size(); ++i) {
            builder.append(machines.get(i).toString());
            if (i < machines.size() - 1) {
                builder.append(',');
            }
        }
        builder.append(']');
        return String.format(
                "MachineGroup{id: %d, machines: %s}", id, builder.toString());
    }


    // 所拥有的设备的id
    final public List<Integer> machines;
    final public Set<Integer> machineSet;
    final public int id;
}
