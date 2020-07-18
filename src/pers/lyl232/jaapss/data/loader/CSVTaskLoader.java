package pers.lyl232.jaapss.data.loader;

import com.csvreader.CsvReader;
import pers.lyl232.jaapss.data.TaskInfo;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class CSVTaskLoader implements TaskInfoLoader {

    /**
     * 加载器构造函数
     *
     * @param csvFile   csv文件路径
     * @param hasHeader 是否有表头
     * @param encoding  编码
     */
    public CSVTaskLoader(String csvFile, boolean hasHeader, String encoding) {
        this.file = csvFile;
        this.hasHeader = hasHeader;
        this.encoding = encoding;
    }

    /**
     * 从csv中读取初始的任务信息数据结构
     *
     * @return Task对象列表
     */
    public Map<Integer, TaskInfo> load() throws Exception {
        CsvReader reader = new CsvReader(file, ',', Charset.forName(this.encoding));

        if (hasHeader) {
            // 目前表头信息无用
            reader.readHeaders();
            reader.getHeaders();
        }

        HashMap<Integer, TaskInfo> taskMap = new HashMap<>();
        String record = "";
        try {
            while (reader.readRecord()) {
                record = reader.getRawRecord();
                String[] item = record.split(",");
                int id = Integer.parseInt(item[0]),
                        machineGroup = !item[3].equals("") ? Integer.parseInt(item[3]) : -1,
                        count = Integer.parseInt(item[4]),
                        nextTask = !item[5].equals("") ? Integer.parseInt(item[5]) : -1,
                        prepareTime = Integer.parseInt(item[6]);
                double expireTime = !item[1].equals("") ? Double.parseDouble(item[1]) : -1,
                        requireTimeEach = Double.parseDouble(item[2]);

                if (taskMap.containsKey(id)) {
                    throw new DataLoadException("Duplicate task information: id");
                }

                taskMap.put(id, new TaskInfo(
                        id, expireTime, requireTimeEach, machineGroup,
                        count, nextTask, prepareTime
                ));
            }
        } catch (NumberFormatException | NullPointerException | ArrayIndexOutOfBoundsException exception) {
            throw new DataLoadException(
                    String.format("task record(%s) necessary information invalid", record));
        }
        reader.close();
        return taskMap;
    }

    final private String file, encoding;
    final private boolean hasHeader;
}
