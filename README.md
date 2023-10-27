# 背景
工作中遇到了这种场景。本地可以直连测试机器，但连接不了目标服务器。而测试机器可以连接上目标服务器。为了在本地可以执行单元测试，所以想在测试机器上搭建一个 http/https 代理服务器，来实现目的。

一开始在网上搜索了一个[基于 python 的实现](https://github.com/WengChaoxi/simple-http-proxy)。
后来为了学习，自己搭建了这个 java 实现的代理服务。

# 使用
```shell
git clone <本项目>
mvn package
java -jar simple-http-proxy-1.0.0-jar-with-dependencies.jar <端口号> <并发数>
```

其中，端口号必填，并发数选填（默认值是5）

# 原理