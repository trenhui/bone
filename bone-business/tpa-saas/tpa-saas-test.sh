#mvn clean  package -Dmaven.test.skip=true  -Dmaven.javadoc.skip=true
echo "SQA@pukang#2701"
#scp bone-tpa-saas/bone-tpa/target/startup.sh root@118.190.216.214:/usr/local/tpa/tpa-saas/start.sh
scp bone-tpa-saas/bone-tpa/target/tpa-saas.jar root@118.190.216.214:/usr/local/tpa/tpa-saas/tpa-saas.jar