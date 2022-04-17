# Crystal Snowflake

为分布式系统而生的全局id生成器组件。受snowflake、sonyflake启发，实现了时钟回拨保护，重新分配了各部分的bit位，能够通过简单的位运算回溯生成id的数据中心以及服务器，简化分布式系统单元化的实现难度。

## 功能特性

- 支持时钟回拨，在同一个序号生成周期内（32ms）对ID生成组件无感。在可以忍耐的回拨极限内，本组件能够支持最大3次的连续回拨。时钟回正，并且达到恢复阈值后，组件依然可以支持最大连续三次的时间回拨。
- 数据中心标识放在最后，方便通过简单的位运算回溯生产id的数据中心
- 所有bit位的组成可以方便的调整，以适应业务的特殊需要
- 可以便捷的与Spring框架集成

## 组成部分

```
                                                 2bits             4bits
                                            |clockback flag|   |data center id|
 0-000000000000000000000000000000000000-00000000-00-0000000000000-0000
   |--time (msecs since 2005-01-01)---| |-seq--|    |-worker id-|
                36 bits                  8 bits         13 bits
```

- 第64位为保留位
- 第28~63位为时间戳位，32ms为单位保存时间信息，能够保存69.7年
- 第20~27位为序号，每32ms一个节点最大产生256个id，1秒产生8000个id
- 第18~19位为时间回拨标识位，发生时钟回拨时加1，最多支持连续三次时钟回拨
- 第5~17位为机器节点，一个dc支持8192台机器，共计131072个计算节点
- 第1~4位为datacenter标识，共计支持16个虚拟dc

## 用法

- Maven

  ```xml
  <dependency>
    <groupId>com.github.johnqxu</groupId>
    <artifactId>crystal-flake</artifactId>
      <version>1.0.0</version>
  </dependency>
  ```

- Gradle

    ```groovy
      implementation 'io.github.johnqxu:crystal-flake:1.0.0'
    ```



Spring Application启动类

```java
@SpringBootApplication
@EnableGlobalId
public class CrystalFlakeDemoApplication {

    public static void main(String[] args) {

        SpringApplication.run(CrystalFlakeDemoApplication.class, args);
    }

}
```

生成ID demo

```java
@RestController
public class TestController {

    final FlakeGenerator flakeGenerator;

    public TestController(FlakeGenerator flakeGenerator) {
        this.flakeGenerator = flakeGenerator;
    }

    @GetMapping("/globalId")
    String getGlobalId() {
        long id = flakeGenerator.nextId();
        return Long.toString(id)+":"+Long.toBinaryString(id);
    }
}
```

