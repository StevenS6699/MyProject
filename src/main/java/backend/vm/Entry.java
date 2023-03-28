package backend.vm;

/*
对于一条记录来说，DB 使用 Entry 类维护了其结构。
虽然理论上，MVCC 实现了多版本，但是在实现中，VM 并没有提供 Update 操作，对于字段的更新操作由后面的表和字段管理（TBM）实现。
所以在 VM 的实现中，一条记录只有一个版本。

一条记录存储在一条 Data Item 中，所以 Entry 中保存一个 DataItem 的引用即可
 */

import com.google.common.primitives.Bytes;
import backend.common.SubArray;
import backend.dm.dataItem.DataItem;
import backend.util.Parser;

import java.util.Arrays;

/**
 * VM向上层抽象出entry
 * entry结构：
 * [XMIN] [XMAX] [data]
 */


public class Entry {
    private static final int OF_XMIN = 0;
    private static final int OF_XMAX = OF_XMIN + 8;
    private static final int OF_DATA = OF_XMAX + 8;

    private long uid;
    private DataItem dataItem;
    private VersionManager vm;

    public static Entry newEntry(VersionManager vm, DataItem dataItem, long uid) {
        Entry entry = new Entry();
        entry.uid = uid;
        entry.dataItem = dataItem;
        entry.vm = vm;
        return entry;
    }

    public static Entry loadEntry(VersionManager vm, long uid) throws Exception {
        DataItem di = ((VersionManagerImpl) vm).dm.read(uid);
        return newEntry(vm, di, uid);
    }

    public void release() {
        ((VersionManagerImpl)vm).releaseEntry(this);
    }

    public void remove() {
        dataItem.release();
    }

    /*
    XMIN 是创建该条记录（版本）的事务编号，而 XMAX 则是删除该条记录（版本）的事务编号。
    DATA 就是这条记录持有的数据
     */
    public static byte[] wrapEntryRaw(long xid, byte[] data) {
        byte[] xmin = Parser.long2Byte(xid);
        byte[] xmax = new byte[8];
        return Bytes.concat(xmin, xmax, data);
    }

    // 同样，如果要获取记录中持有的数据，也就需要按照这个结构来解析：
    // 以拷贝的形式返回内容
    public byte[] data() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            byte[] data = new byte[sa.end - sa.start - OF_DATA];
            System.arraycopy(sa.raw, sa.start + OF_DATA, data, 0, data.length);
            return data;
        } finally {
            dataItem.rUnLock();
        }
    }

    //这里以拷贝的形式返回数据，如果需要修改的话，需要对 DataItem 执行 before() 方法，这个在设置 XMAX 的值中体现了
    //before() 和 after() 是在 DataItem 一节中就已经确定的数据项修改规则
    public void setXmax(long xid) {
        dataItem.before();
        try {
            SubArray sa = dataItem.data();
            System.arraycopy(Parser.long2Byte(xid), 0, sa.raw, sa.start + OF_XMAX, 8);
        } finally {
            dataItem.after(xid);
        }
    }

    public long getXmax() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start + OF_XMAX, sa.start + OF_DATA));
        } finally {
            dataItem.rUnLock();
        }
    }

    public long getUid() {
        return uid;
    }

    public long getXmin() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start + OF_XMIN, sa.start + OF_XMAX));
        } finally {
            dataItem.rUnLock();
        }
    }



}
