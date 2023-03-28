* Parser 实现了对类 SQL 语句的结构化解析，将语句中包含的信息封装为对应语句的类,这些类可见 statement package
* parser 包的 Tokenizer 类，对语句进行逐字节解析，根据空白符或者上述词法规则，
* 将语句切割成多个 token。对外提供了 peek()、pop() 方法方便取出 Token 进行解析。切割的实现不赘述。
  Parser 类则直接对外提供了 Parse(byte[] statement) 方法，
* 核心就是一个调用 Tokenizer 类分割 Token，并根据词法规则包装成具体的 Statement 类并返回。解析过程很简单
* 仅仅是根据第一个 Token 来区分语句类型，并分别处理，不再赘述。
  虽然根据编译原理，词法分析应当写一个自动机去做的，但是又不是不能用