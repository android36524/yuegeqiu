#! /bin/bash

app=yueqiu
module=yueqiu-web
version=0.0.1
jar=$module-$version-SNAPSHOT.jar
pid=`ps -ef | grep $jar | grep -v grep | awk '{print $2}'`

cd /opt/$app

if [ -n "$pid" ] ; then
    kill $pid
fi

cp /tmp/$app/target/$jar ./

nohup java -jar $jar > /dev/null 2>&1 &
echo $! > $module.pid