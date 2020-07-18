package pers.lyl232.jaapss.data.loader;

import pers.lyl232.jaapss.data.TaskInfo;

import java.util.Map;

/**
 * 任务信息加载器接口
 */
public interface TaskInfoLoader {
    /**
     * 加载并维护Task数据
     *
     * @return Task对象Map
     */
    Map<Integer, TaskInfo> load() throws Exception;
}
