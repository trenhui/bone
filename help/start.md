springcloud依赖组件启动

1、nacos
安装目录
/usr/local/nacos-server-2.2.0/nacos
启停命令：
sh bin/startup.sh -m standalone
sh bin/shutdown.sh     

2、rockmq
安装目录
/usr/local/rocketmq
启停命令：
nohup sh ./bin/mqnamesrv &    
nohup sh bin/mqbroker -c conf/broker.conf  -n 127.0.0.1:9876 &
sh bin/mqshutdown namesrv     
sh  bin/mqshutdown broker       
查看日志
tail -f ~/logs/rocketmqlogs/broker.log
tail -f ~/logs/rocketmqlogs/namesrv.log


3、xxl-job
安装目录
/Users/renhui.trh/code/opensource/xxl-job/xxl-job-admin/target
启停命令：
java -jar xxl-job-admin-2.4.0-SNAPSHOT.jar

4、redis
安装目录
/usr/local/redis
启停命令：
redis-server

1.启动redis服务
brew services start redis
2.关闭redis服务
brew services stop redis
3.重启redis服务
brew services restart redis
4.打开图形化界面
redis-cli

5、yudao-ui-admin-vue3
安装目录
/Users/renhui.trh/code/meishan/bone-cloud-vue3/yudao-ui-admin-vue3
启停命令：
cnpm install
npm run dev

6、bone-lowcode
安装目录
/Users/renhui.trh/code/meishan/bone-react/bone-lowcode
启停命令：
npm run start