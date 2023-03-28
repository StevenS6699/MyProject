package backend.dm.page;
// 数据页

public interface Page {
    void lock();

    void unlock();

    void setDirty(boolean dirty);

    boolean isDirty();

    int getPageNumber();

    byte[] getData();

    void release();

}
