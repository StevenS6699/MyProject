package backend.dm.pageCache;

import backend.common.AbstractCache;
import backend.dm.page.Page;
import backend.dm.page.PageImp;
import backend.util.Panic;
import common.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
页面缓存的具体实现类，需要继承抽象缓存框架，并且实现 getForCache() 和 releaseForCache() 两个抽象方法。
由于数据源就是文件系统，getForCache() 直接从文件中读取，并包裹成 Page 即可
 */

public class PageCacheImp extends AbstractCache<Page> implements PageCache {
    private static final int MEM_MIN_LIM = 10;
    public static final String DB_SUFFIX = ".db";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock fileLock;

    private AtomicInteger pageNumbers;

    PageCacheImp(RandomAccessFile file, FileChannel fileChannel, int maxResource) {
        super(maxResource);
        if (maxResource < MEM_MIN_LIM) {
            Panic.panic(Error.MemTooSmallException);
        }
        long length = 0;
        try {
            length = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }
        this.file = file;
        this.fc = fileChannel;
        this.fileLock = new ReentrantLock();
        this.pageNumbers = new AtomicInteger((int) length / PAGE_SIZE);
    }

    /*
     * PageCache 还使用了一个 AtomicInteger，来记录了当前打开的数据库文件有多少页。
     * 这个数字在数据库文件被打开时就会被计算，并在新建页面时自增。
     */
    public int newPage(byte[] initData) {
        int pgNo = pageNumbers.incrementAndGet();
        Page pg = new PageImp(pgNo, initData, null);
    // 新建的页面需要立刻写回
        flush(pg);
        return pgNo;
    }

    public Page getPage(int pgno) throws Exception {
        return get((long) pgno);
    }

    /**
     * 根据pageNumber从数据库文件中读取页数据，并包裹成Page
     */
    @Override
    protected Page getForCache(long key) throws Exception {
        int pgNo = (int) key;
        long offset = PageCacheImp.pageOffset(pgNo);

        ByteBuffer buf = ByteBuffer.allocate(PAGE_SIZE);
        fileLock.lock();
        try {
            fc.position(offset);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        fileLock.unlock();
        return new PageImp(pgNo, buf.array(), this);
    }

    // releaseForCache() 驱逐页面时，只需要根据页面是否是脏页面，来决定是否需要写回文件系统：
    // 在数据库管理系统中，脏页面（Dirty Page）是指缓存中已经被修改，但尚未写入磁盘的数据页。
    @Override
    protected void releaseForCache(Page pg) {
        if (pg.isDirty()) {
            flush(pg);
            pg.setDirty(false);
        }
    }

    public void release(Page page) {
        release((long) page.getPageNumber());
    }

    public void flushPage(Page pg) {
        flush(pg);
    }

    private void flush(Page pg) {
        int pgNo = pg.getPageNumber();
        long offset = pageOffset(pgNo);

        fileLock.lock();
        try {
            ByteBuffer buf = ByteBuffer.wrap(pg.getData());
            fc.position(offset);
            fc.write(buf);
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        } finally {
            fileLock.unlock();
        }
    }

    public void truncateByBgno(int maxPgno) {
        long size = pageOffset(maxPgno + 1);
        try {
            file.setLength(size);
        } catch (IOException e) {
            Panic.panic(e);
        }
        pageNumbers.set(maxPgno);
    }

    @Override
    public void close() {
        super.close();
        try {
            fc.close();
            file.close();
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    public int getPageNumber() {
        return pageNumbers.intValue();
    }

    private static long pageOffset(int pgno) {
        return (pgno - 1) * PAGE_SIZE;
    }

}
