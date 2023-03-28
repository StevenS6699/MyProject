package backend.dm;

import backend.common.AbstractCache;
import backend.dm.dataItem.DataItem;
import backend.dm.dataItem.DataItemImpl;
import backend.dm.logger.Logger;
import backend.dm.page.Page;
import backend.dm.page.PageN;
import backend.dm.page.PageOne;
import backend.dm.pageCache.PageCache;
import backend.dm.pageIndex.PageIndex;
import backend.dm.pageIndex.PageInfo;
import backend.tm.TransactionManager;
import backend.util.Panic;
import backend.util.Types;
import common.Error;

public class DataManagerImpl extends AbstractCache<DataItem> implements DataManager {
    TransactionManager tm;
    PageCache pc;
    Logger logger;
    PageIndex pIndex;
    Page pageOne;

    public DataManagerImpl(PageCache pc, Logger logger, TransactionManager tm) {
        super(0);
        this.pc = pc;
        this.logger = logger;
        this.tm = tm;
        this.pIndex = new PageIndex();
    }


    /*
    DataItem 缓存，getForCache()，只需要从 key 中解析出页号，从 pageCache 中获取到页面，再根据偏移，解析出 DataItem 即可
     */
    @Override
    protected DataItem getForCache(long uid) throws Exception {
        short offset = (short) (uid & ((1L << 16) - 1));
        uid >>>= 32;
        int pgno = (int) (uid & ((1L << 32) - 1));
        Page pg = pc.getPage(pgno);
        return DataItem.parseDataItem(pg, offset, this);
    }

    /*
    DataItem 缓存释放，需要将 DataItem 写回数据源，
    由于对文件的读写是以页为单位进行的，只需要将 DataItem 所在的页 release 即可
     */
    @Override
    protected void releaseForCache(DataItem di) {
        di.page().release();
    }

    // 在创建文件时初始化PageOne
    void initPageOne() {
        int pgno = pc.newPage(PageOne.InitRaw());
        assert pgno == 1;
        try {
            pageOne = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }

    // 在打开已有文件时时读入PageOne，并验证正确性
    boolean loadCheckPageOne() {
        try {
            pageOne = pc.getPage(1);
        } catch (Exception e) {
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }

    /*
    DM 层提供了三个功能供上层使用，分别是读、插入和修改。
    修改是通过读出的 DataItem 实现的，于是 DataManager 只需要提供 read() 和 insert() 方法。
    read() 根据 UID 从缓存中获取 DataItem，并校验有效位：
     */
    @Override
    public DataItem read(long uid) throws Exception {
        DataItemImpl di = (DataItemImpl) super.get(uid);
        if (!di.isValid()) {
            di.release();
            return null;
        }
        return di;
    }

    /*
    insert() 方法，在 pageIndex 中获取一个足以存储插入内容的页面的页号，
    获取页面后，首先需要写入插入日志，接着才可以通过 pageN 插入数据，并返回插入位置的偏移。
    最后需要将页面信息重新插入 pageIndex。
     */
    @Override
    public long insert(long xid, byte[] data) throws Exception {
        byte[] raw = DataItem.wrapDataItemRaw(data);
        if (raw.length > PageN.MAX_FREE_SPACE) {
            throw Error.DataTooLargeException;
        }

        PageInfo pi = null;
        for (int i = 0; i < 5; i++) {
            pi = pIndex.select(raw.length);
            if (pi != null) {
                break;
            } else {
                int newPgno = pc.newPage(PageN.initRaw());
                pIndex.add(newPgno, PageN.MAX_FREE_SPACE);
            }
        }
        if (pi == null) {
            throw Error.DatabaseBusyException;
        }

        Page pg = null;
        int freeSpace = 0;
        try {
            pg = pc.getPage(pi.pgno);
            byte[] log = Recover.insertLog(xid, pg, raw);
            logger.log(log);

            short offset = PageN.insert(pg, raw);

            pg.release();
            return Types.addressToUid(pi.pgno, offset);

        } finally {
            // 将取出的pg重新插入pIndex
            if (pg != null) {
                pIndex.add(pi.pgno, PageN.getFreeSpace(pg));
            } else {
                pIndex.add(pi.pgno, freeSpace);
            }
        }
    }

    // 为xid生成update日志
    public void logDataItem(long xid, DataItem di) {
        byte[] log = Recover.updateLog(xid, di);
        logger.log(log);
    }

    public void releaseDataItem(DataItem di) {
        super.release(di.getUid());
    }

    // 初始化pageIndex
    void fillPageIndex() {
        int pageNumber = pc.getPageNumber();
        for (int i = 2; i <= pageNumber; i++) {
            Page pg = null;
            try {
                pg = pc.getPage(i);
            } catch (Exception e) {
                Panic.panic(e);
            }
            pIndex.add(pg.getPageNumber(), PageN.getFreeSpace(pg));
            pg.release();
        }
    }

    // DataManager 正常关闭时，需要执行缓存和日志的关闭流程，不要忘了设置第一页的字节校验
    @Override
    public void close() {
        super.close();
        logger.close();

        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
    }


}
