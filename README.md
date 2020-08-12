## 项目编译

### 系统版本
macOS Mojave10.14.6

### 其他
jdk 1.8.0_231
Maven 3.5.4

### 过程
1、根目录下执行
> mvn install -DskipTests -Dcheckstyle.skip=true

2、虽然跳过了测试，但是还是会报junit相关的异常
> 注释掉相关的代码。
> 注释掉后，根目录编译还是不行的话，则进入子项目，先mvn clean install -DskipTests，把子包安装了。
> 编译过程中发现codec下的测试代码全部报错，直接把test这个文件夹重命名为test111了。

3、编译到transport-native-kqueue项目的时候，一直过不了，查资料说要升级gcc，升级gcc需要安装xcode，太重了。
> 最终解决方案：在根pom中注释掉<module>transport-native-kqueue</module>

4、编译success后，IDEA中跑demo还是不行，codec模块的代码还是爆红，只能把这个模块也先注释掉了。。

5、其他
将这两个配置修改成1.8
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>

注释掉：
<module>testsuite</module>
    <module>testsuite-autobahn</module>
    <module>testsuite-http2</module>
    <module>testsuite-osgi</module>
    <module>testsuite-shading</module>
