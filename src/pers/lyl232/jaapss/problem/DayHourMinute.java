package pers.lyl232.jaapss.problem;

/**
 * 表示一天工作的一段时间(精确到时分)
 */
public class DayHourMinute {
    /**
     * @param hour1   时间点1的小时
     * @param minute1 时间点1的分钟
     * @param hour2   时间点2的小时
     * @param minute2 时间点2的分钟
     */
    public DayHourMinute(int hour1, int minute1, int hour2, int minute2) {
        hour1 = Math.min(24, hour1);
        hour2 = Math.min(24, hour2);
        minute1 = Math.min(59, minute1);
        minute2 = Math.min(59, minute2);
        if (hour1 > hour2 || (hour1 == hour2 && minute1 > minute2)) {
            this.hour1 = hour2;
            this.minute1 = minute2;
            this.hour2 = hour1;
            this.minute2 = minute1;
        } else {
            this.hour1 = hour1;
            this.minute1 = minute1;
            this.hour2 = hour2;
            this.minute2 = minute2;
        }
    }

    /**
     * @return 时长: 毫秒数
     */
    public long getWorkTime() {
        return ((hour2 - hour1) * 3600 + (minute2 - minute1) * 60) * 1000;
    }

    public String toString() {
        return String.format("%02d:%02d-%02d:%02d", hour1, minute1, hour2, minute2);
    }

    final public int hour1, minute1, hour2, minute2;
}
