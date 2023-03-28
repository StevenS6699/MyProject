package backend.vm;

/*
 * 如果一个记录的最新版本被加锁，当另一个事务想要修改或读取这条记录时，MYDB 就会返回一个较旧的版本的数据。
 * 这时就可以认为，最新的被加锁的版本，对于另一个事务来说，是不可见的。
 * 版本的可见性与事务的隔离度是相关的。MYDB 支持的最低的事务隔离程度，是“读提交”（Read Committed），
 * 即事务在读取数据时, 只能读取已经提交事务产生的数据。防止级联回滚与 commit 语义冲突
 * DB 实现读提交，为每个版本维护了两个变量，就是XMIN 和 XMAX
 * XMIN：创建该版本的事务编号, XMAX：删除该版本的事务编号
 * XMIN 应当在版本创建时填写，而 XMAX 则在版本被删除，或者有新版本出现时填写。
 * XMAX 这个变量，也就解释了为什么 DM 层不提供删除操作，当想删除一个版本时，只需要设置其 XMAX，
 * 这样，这个版本对每一个 XMAX 之后的事务都是不可见的，也就等价于删除了。
 */

import backend.tm.TransactionManager;

public class Visibility {

    public static boolean isVisible(TransactionManager tm, Transaction t, Entry e) {
        if (t.level == 0) {
            return readCommitted(tm, t, e);
        } else {
            return repeatableRead(tm, t, e);
        }
    }

    private static boolean readCommitted(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();
        if (xmin == xid && xmax == 0) return true;

        if (tm.isCommitted(xmin)) {
            if (xmax == 0) return true;
            if (xmax != xid) {
                if (!tm.isCommitted(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }


    //构造方法中的 active，保存着当前所有 active 的事务。于是，可重复读的隔离级别下，一个版本是否对事务可见的判断如下
    private static boolean repeatableRead(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();
        if (xmin == xid && xmax == 0) return true;

        if (tm.isCommitted(xmin) && xmin < xid && !t.isInSnapshot(xmin)) {
            if (xmax == 0) return true;
            if (xmax != xid) {
                if (!tm.isCommitted(xmax) || xmax > xid || t.isInSnapshot(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
    读提交是允许版本跳跃的，而可重复读则是不允许版本跳跃的。解决版本跳跃的思路也很简单：
    如果 Ti 需要修改 X，而 X 已经被 Ti 不可见的事务 Tj 修改了，那么要求 Ti 回滚。

    于是版本跳跃的检查也就很简单了，取出要修改的数据 X 的最新提交版本，并检查该最新版本的创建者对当前事务是否可见
     */
    public static boolean isVersionSkip(TransactionManager tm, Transaction t, Entry e) {
        long xmax = e.getXmax();
        if (t.level == 0) {
            return false;
        } else {
            return tm.isCommitted(xmax) && (xmax > t.xid || t.isInSnapshot(xmax));
        }
    }
}
