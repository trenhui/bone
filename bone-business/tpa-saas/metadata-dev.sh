mvn clean  package -Dmaven.test.skip=true  -Dmaven.javadoc.skip=true
echo "SQA@pukang#2701"
scp    bone-lowcode/bone-lowcode-metadata/target/metadata-server.jar root@47.104.17.249:/usr/local/pk/metadata-server/metadata-server.jar
#scp    bone-lowcode/bone-lowcode-metadata/target/metadata-server.jar root@118.190.216.214:/usr/local/tpa/metadata-server/metadata-server.jar
