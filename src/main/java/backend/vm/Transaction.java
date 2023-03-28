package backend.vm;

/*
读提交会导致的问题大家也都很清楚，八股也背了不少。那就是不可重复读和幻读。这里我们来解决不可重复读的问题
不可重复度，会导致一个事务在执行期间对同一个数据项的读取得到不同结果。
事务只能读取它开始时, 就已经结束的那些事务产生的数据版本
这条规定，增加于，事务需要忽略：
在本事务后开始的事务的数据;本事务开始时还是 active 状态的事务的数据
对于第一条，只需要比较事务 ID，即可确定。
而对于第二条，则需要在事务 Ti 开始时，记录下当前活跃的所有事务 SP(Ti)，
如果记录的某个版本，XMIN 在 SP(Ti) 中，也应当对 Ti 不可见。

于是，需要提供一个结构，来抽象一个事务，以保存快照数据：
 */

import backend.tm.TMImplement;

import java.util.HashMap;
import java.util.Map;

public class Transaction {
    public long xid;
    public int level;
    public Map<Long, Boolean> snapshot;
    public Exception err;
    public boolean autoAborted;

    public static Transaction newTransaction(long xid, int level, Map<Long, Transaction> active) {
        Transaction t = new Transaction();
        t.xid = xid;
        t.level = level;
        if (level != 0) {
            t.snapshot = new HashMap<>();
            for (Long x : active.keySet()) {
                t.snapshot.put(x, true);
            }
        }
        return t;
    }

    public boolean isInSnapshot(long xid) {
        if (xid == TMImplement.SUPER_XID) {
            return false;
        }
        return snapshot.containsKey(xid);
    }
}
