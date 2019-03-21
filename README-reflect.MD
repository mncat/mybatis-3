# Mybatis反射模块
+ 数据库中的数据到java类的转换过程
```
 数据库加载数据 --> 找到映射匹配规则 --> 实例化目标对象 --> 对象属性的复制
```
反射模块使用频繁，而JDK的反射模块在mybatis中用起来并不得心应手，因此封装了反射模块 

## 一、简介
+  

### 1.1 设计思想  
+  
## 二、 
### 2.1  
 
## 三、目录结构
+  


## 四、源码解析


1.反射模式对比jdbc，这里解决的是创建对象的问题，而在jdbc代码中，是通过new一个对象，再读取mysql的返回值的属性，赋值给这个
对象，反射模块就对应创建对象的这个环节，这个环节是通过ObjectFactory来完成的
2.mybatis使用暴力反射，不管有没有get set方法都会拿到对应的set get方法，如果类没有get set方法，Reflect组件会帮助生成get set方法
3.ObjectWrapper给对象动态赋值

ObjectFactory创建对象 --> ObjectWrapper给对象动态赋值(通过Reflect读取字段，通过ObjectWrapper给对象赋值)