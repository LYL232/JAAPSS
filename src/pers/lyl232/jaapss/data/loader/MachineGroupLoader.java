package pers.lyl232.jaapss.data.loader;

import pers.lyl232.jaapss.data.MachineGroup;

import java.util.Map;

/**
 * 设备信息加载器接口
 */
public interface MachineGroupLoader {
    /**
     * 加载设备组
     *
     * @return 设备组id到设备id列表的映射
     */
    Map<Integer, MachineGroup> load() throws Exception;
}
