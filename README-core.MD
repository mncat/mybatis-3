# Mybatis整体流程
## 一、简介 
+ Mybatis的整体流程
初始化阶段：读取xml配置文件和注解中的配置信息，创建配置对象，完成各个模块的初始化过程
代理阶段：封装ibatis编程模型，使用mapper接口开发的初始化工作
数据读写阶段：通过SqlSession完成sql解析，参数的映射，sql执行，结果的解析过程
读取配置 -->初始化配置类

## 二、Mybatis初始化--建造者模式
### 2.1 来源
+ 初始化阶段会把主配置文件和所有的mapper文件加载到内存，并生成一个配置对象 
+ 建造者模式比较简单，首先定义一个Builder接口，然后针对不同的生产的产品有不同的builde子类，子类中完成创建对象的过程，
通常使用流式编程来实现

创建者模式：在于简化创建的过程，但是客户端还是需要参与创建的过程的，通产对象很复杂，参数属性很多，或者对象的创建依赖很多其他变量。
  --- zookeeper的Curator客户端
工程模式：创建和使用分离，客户端不关心也不参与创建的过程，不关心细节。


读取配置第一步：XMLConfiguration读取主配置文件，将配置设置到Configuration对象，自己做，没有秘书
读取配置第二步：XMLMapperBuilder来解析Mapper.xml映射文件，有秘书
读取配置第三步：XMLStatementBuilder来解析。有秘书
三个火枪手完成xml的解析，最终将配置放到configuration里面，这个对象是在他们的共同父类BaseBuilder里面定义的
这里的建造者模式可以理解为有多个建造者协作来完成Configuration对象的创建，但是表面并没有采用流式编程，只有建造者模式
的灵魂，没有外表
CacheBuilder类是正儿八经的建造者模式。
MapperBuilderAssistant类很重要，协助创建对象，里面有很多的建造者模式实现。
CacheBuilder.setStandardDecorators：这里的代码很经典，为缓存添加各种装饰器的功能

MappedStatement：保存sql语句的数据结构
### 2.2 优点 

### 2.3 缺点 

### 2.4 借鉴 

### 2.5 代码
 

## 三、目录结构 

## 四、源码解析
