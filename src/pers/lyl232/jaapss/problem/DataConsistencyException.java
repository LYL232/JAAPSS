package pers.lyl232.jaapss.problem;

/**
 * 信息一致性异常, 如找不到指定id的数据
 */
public class DataConsistencyException extends Exception {
    public DataConsistencyException(String msg) {
        super(msg);
    }
}
