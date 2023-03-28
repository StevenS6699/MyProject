package backend.tm;

import backend.util.Panic;
import backend.util.Parser;
import common.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TMImplement implements TransactionManager {
//  定义需要的常量

    //  XID文件头长度
    static final int LEN_XID_HEADER_LENGTH = 8;
    //  每个事务的占用长度
    private static final int XID_FIELD_SIZE = 1;
    //  事务的状态：active committed aborted
    private static final byte FIELD_TRAN_ACTIVE = 0;
    private static final byte FIELD_TRAN_COMMITTED = 1;
    private static final byte FIELD_TRAN_ABORTED = 2;
    //Super Transaction
    public static final long SUPER_XID = 0;
    //  XID 的文件后缀
    static final String XID_SUFFIX = ".xid";


    //    文件读写都采用了 NIO 方式的 FileChannel
    private RandomAccessFile file;
    private FileChannel fc;
    private long xidCounter;
    private Lock counterLock;

    //    constructor
    TMImplement(RandomAccessFile file, FileChannel fc) {
        this.file = file;
        this.fc = fc;
        counterLock = new ReentrantLock();
        //  对 XID 文件进行校验，以保证这是一个合法的 XID 文件
        checkXIDCounter();
    }

    /*
    通过文件头的 8 字节数字反推文件的理论长度，与文件的实际长度做对比
    读取XID_FILE_HEADER中的xidcounter，根据它计算文件的理论长度，对比实际长度

    对于校验没有通过的，会直接通过 panic 方法，强制停机
    在一些基础模块中出现错误都会如此处理，无法恢复的错误只能直接停机。
     */
    private void checkXIDCounter() {
        long fileLen = 0;
        try {
            fileLen = file.length();
        } catch (IOException e1) {
            Panic.panic(Error.BadXIDFileException);
        }
        if (fileLen < LEN_XID_HEADER_LENGTH) {
            Panic.panic(Error.BadXIDFileException);
        }

        ByteBuffer buf = ByteBuffer.allocate(LEN_XID_HEADER_LENGTH);
        try {
            fc.position(0);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        this.xidCounter = Parser.parseLong(buf.array());
        long end = getXidPosition(this.xidCounter + 1);
        if (end != fileLen) {
            Panic.panic(Error.BadXIDFileException);
        }
    }

    //    根据事务XID取得其在XID文件中对应的位置
    private long getXidPosition(long xid) {
        return LEN_XID_HEADER_LENGTH + (xid - 1) * XID_FIELD_SIZE;
    }

    //    开始一个事务，并返回XID
    public long begin() {
        counterLock.lock();
        try {
            long xid = xidCounter + 1;
            updateXID(xid, FIELD_TRAN_ACTIVE);
            incrXIDCounter();
            return xid;
        } finally {
            counterLock.unlock();
        }
    }

    //   提交XID事务
    public void commit(long xid) {
        updateXID(xid, FIELD_TRAN_COMMITTED);
    }

    //   撤销（回滚）XID事务
    public void abort(long xid) {
        updateXID(xid, FIELD_TRAN_ABORTED);
    }


    //更新xid事务的状态为status
    private void updateXID(long xid, byte status) {
        long offset = getXidPosition(xid);
        byte[] tmp = new byte[XID_FIELD_SIZE];
        tmp[0] = status;
        ByteBuffer buf = ByteBuffer.wrap(tmp);
        try {
            fc.position(offset);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        try {
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    //将XID加一，并更新XID Header
    private void incrXIDCounter() {
        xidCounter++;
        ByteBuffer buf = ByteBuffer.wrap(Parser.long2Byte(xidCounter));
        try {
            fc.position(0);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        try {
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }
    /*
    这里的所有文件操作，在执行后都需要立刻刷入文件中，防止在崩溃后文件丢失数据
    fileChannel 的 force() 方法，强制同步缓存内容到文件中，类似于 BIO 中的 flush() 方法
    force 方法的参数是一个布尔，表示是否同步文件的元数据（例如最后修改时间等）

    commit() 和 abort() 方法就可以直接借助 updateXID() 方法实现
    isActive()、isCommitted() 和 isAborted() 都是检查一个 xid 的状态，可以用一个通用的方法解决
     */

    //    检测XID事务是否处于status状态
    private boolean checkXID(long xid, byte status) {
        long offset = getXidPosition(xid);
        ByteBuffer buf = ByteBuffer.wrap(new byte[XID_FIELD_SIZE]);
        try {
            fc.position(offset);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        return buf.array()[0] == status;
    }

    //    排除super
    public boolean isActive(long xid) {
        if (xid == SUPER_XID) return false;
        return checkXID(xid, FIELD_TRAN_ACTIVE);
    }

    //    super事务永远都是committed
    public boolean isCommitted(long xid) {
        if (xid == SUPER_XID) return true;
        return checkXID(xid, FIELD_TRAN_COMMITTED);
    }

    public boolean isAborted(long xid) {
        if (xid == SUPER_XID) return false;
        return checkXID(xid, FIELD_TRAN_ABORTED);
    }


    //    close
    public void close() {
        try {
            fc.close();
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
