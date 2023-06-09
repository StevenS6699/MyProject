* 数据库中的索引是一种用于提高数据库查询效率的数据结构，它可以加速数据库查询操作的速度。
* 索引通常是基于一个或多个表的一个或多个列上创建的。
* 索引允许数据库系统快速定位具有特定属性值的数据行，从而避免了扫描整个表来搜索所需数据的开销。
* 创建索引可以提高查询性能，因为它可以帮助数据库系统更快地找到所需的数据。
* 然而，过多或不正确的索引可能会导致性能下降，因为它们会占用磁盘空间、增加查询时间和占用内存等。
* 总之，索引是数据库中一个重要的优化手段，但要在权衡索引数量和索引性能的前提下进行创建和维护。


* IM，即 Index Manager，索引管理器，为 DB 提供了基于 B+ 树的聚簇索引。目前 DB 只支持基于索引查找数据，不支持全表扫描。