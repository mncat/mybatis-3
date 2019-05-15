# Mybatis Binding模块
## 一、简介 
+  mybatis中没有实现类就可以操作数据库，其原因在于框架读取了配置文件之后，底层使用了动态代理创建类访问数据
库那么这个动态代理增强的过程就是binding模块的功能，整体来看，该模块实现的是将面向mapper接口的编程转换为底
层的sqlSession来访问数据库

## 二、Mybatis初始化--建造者模式
### 2.1 来源
+ 


### 2.2 优点 

### 2.3 缺点 

### 2.4 借鉴 

### 2.5 代码
 

## 三、目录结构 
+ MapperRegistry：mapper接口和对应的代理对象工厂注册中心
+ MapperProxyFactory：用于生成mapper接口动态代理的实例对象
+ MapperProxy：InvocationHandler的实现类，增强的mapper接口实现
+ MapperMethod：封装mapper接口中对应方法的信息，sql语句信息，是mapper接口和映射配置文件中sql语句之间的桥梁,
  private final SqlCommand command;
  private final MethodSignature method;
这两个字段，一个是sql语句信息，一个是接口中的方法信息，这个类将这个两个信息给关联起来，接口方法信息是从configuration中获取的

## 四、源码解析

## 参考
+ https://www.jianshu.com/p/83785a294f8e
