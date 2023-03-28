package backend.tm;

/*
在 DB 中，每一个事务都有一个 XID，这个 ID 唯一标识了这个事务
事务的 XID 从 1 开始标号，并自增，不可重复
特殊规定 XID 0 是一个超级事务（Super Transaction）
当一些操作想在没有申请事务的情况下进行，那么可以将操作的 XID 设置为 0
XID 为 0 的事务的状态永远是 committed

TransactionManager 维护了一个 XID 格式的文件，用来记录各个事务的状态
DB 中，每个事务都有下面的三种状态：
active，正在进行，尚未结束
committed，已提交
aborted，已撤销（回滚）

XID 文件给每个事务分配了一个字节的空间，用来保存其状态
在 XID 文件的头部，还保存了一个 8 字节的数字，记录了这个 XID 文件管理的事务的个数
于是，事务 xid 在文件中的状态就存储在 (xid-1)+8 字节处，xid-1 是因为 xid 0（Super XID） 的状态不需要记录

TransactionManager 提供了一些接口供其他模块调用，用来创建事务和查询事务状态

 */

import backend.util.Panic;
import common.Error;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public interface TransactionManager {
    //    开启一个新事务
    long begin();

    //    提交一个新事务
    void commit(long xid);

    //    取消一个新事务
    void abort(long xid);

    //    查询事务状态是否正在进行
    boolean isActive(long xid);

    //    查询事务状态是否已提交
    boolean isCommitted(long xid);

    //    查询事务状态是否已取消
    boolean isAborted(long xid);

    //    关闭
    void close();

    /*
    另外就是两个静态方法：create() 和 open()，分别表示创建一个 xid 文件并创建 TM 和从一个已有的 xid 文件来创建 TM
    从零创建 XID 文件时需要写一个空的 XID 文件头，即设置 xidCounter 为 0，否则后续在校验时会不合法
    */
    public static TMImplement create(String path) {
        File f = new File(path + TMImplement.XID_SUFFIX);
        try {
            if (!f.createNewFile()) {
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e) {
            Panic.panic(e);
        }
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }

        // 写空XID文件头
        ByteBuffer buf = ByteBuffer.wrap(new byte[TMImplement.LEN_XID_HEADER_LENGTH]);
        try {
            fc.position(0);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        return new TMImplement(raf, fc);
    }

    public static TMImplement open(String path) {
        File f = new File(path + TMImplement.XID_SUFFIX);
        if (!f.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }

        return new TMImplement(raf, fc);
    }
}
