package backend.dm.page;

import backend.dm.pageCache.PageCache;
import backend.dm.pageCache.PageCacheImp;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
参考大部分数据库的设计，将默认数据页大小定为 8K。如果想要提升向数据库写入大量数据情况下的性能的话，也可以适当增大这个值。
首先，需要定义出页面的结构。注意这个页面是存储在内存中的，与已经持久化到磁盘的抽象页面有区别。

pageNumber 是这个页面的页号，该页号从 1 开始。
data 就是这个页实际包含的字节数据。
dirty 标志着这个页面是否是脏页面，在缓存驱逐的时候，脏页面需要被写回磁盘。
保存了一个 PageCache的引用，用来方便在拿到 Page 的引用时可以快速对这个页面的缓存进行释放操作
 */

public class PageImp implements Page {
    private int pageNumber;
    private byte[] data;
    private boolean dirty;
    private Lock lock;

    private PageCache pc;

    public PageImp(int pageNumber, byte[] data, PageCacheImp pc) {
        this.pageNumber = pageNumber;
        this.data = data;
        this.pc = pc;
        lock = new ReentrantLock();
    }


    @Override
    public void lock() {
        lock.lock();
    }

    @Override
    public void unlock() {
        lock.unlock();

    }

    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;

    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public int getPageNumber() {
        return pageNumber;
    }

    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public void release() {
        pc.release(this);
    }

}
