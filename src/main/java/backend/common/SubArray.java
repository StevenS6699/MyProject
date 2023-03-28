package backend.common;
// 共享内存数组
// java 数组中内存独立，例如substring（）无法和原数组在同一内存

public class SubArray {
    public byte[] raw;
    public int start;
    public int end;

    public SubArray(byte[] raw, int start, int end) {
        this.raw = raw;
        this.start = start;
        this.end = end;
    }

}
