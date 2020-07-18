package pers.lyl232.jaapss.data.loader;

import com.csvreader.CsvReader;
import pers.lyl232.jaapss.data.MachineGroup;

import java.nio.charset.Charset;
import java.util.*;

/**
 * CSV设备信息加载器
 */
public class CSVMachineLoader implements MachineGroupLoader {

    /**
     * csv设备信息加载器
     *
     * @param csvFile   设备信息文件路径
     * @param hasHeader 是否有表头
     * @param encoding  编码
     */
    public CSVMachineLoader(String csvFile, boolean hasHeader, String encoding) {
        this.file = csvFile;
        this.hasHeader = hasHeader;
        this.encoding = encoding;
    }


    public Map<Integer, MachineGroup> load() throws Exception {
        CsvReader reader = new CsvReader(file, ',', Charset.forName(this.encoding));

        if (hasHeader) {
            // 目前表头信息无用
            reader.readHeaders();
            reader.getHeaders();
        }

        Map<Integer, List<Integer>> data = new HashMap<>();
        String record = "";
        try {
            while (reader.readRecord()) {
                record = reader.getRawRecord();
                String[] item = record.split(",");
                int machineId = Integer.parseInt(item[0]),
                        // 如果设备组未定义, 则设为设备组0
                        groupId = !item[1].equals("") ?
                                Integer.parseInt(item[1]) : 0,
                        enable = !item[2].equals("") ? Integer.parseInt(item[2]) : 0;
                if (enable == 0) {
                    continue;
                }
                if (!data.containsKey(groupId)) {
                    data.put(groupId, new ArrayList<>());
                }
                data.get(groupId).add(machineId);
            }
        } catch (NumberFormatException | NullPointerException exception) {
            throw new DataLoadException(
                    String.format("machine group record(%s) necessary information invalid",
                            record));
        }
        reader.close();

        // 将数据包装成MachineGroup
        Map<Integer, MachineGroup> groupMap = new HashMap<>();
        // 所有的设备
        List<Integer> allMachines = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : data.entrySet()) {
            groupMap.put(entry.getKey(),
                    new MachineGroup(entry.getKey(), Collections.unmodifiableList(entry.getValue())));
            allMachines.addAll(entry.getValue());
        }
        // 所有设备的id为-1
        groupMap.put(-1, new MachineGroup(-1, allMachines));

        return groupMap;
    }

    final private String file, encoding;
    final private boolean hasHeader;

}
