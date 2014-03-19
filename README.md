uns
===
activemq的服务器端、客户端（Producer、Consumer）

scala
spray
camel
akka
mongodb

deploy

sbt update
sbt package
sbt gen-idea
sbt eclipse
sbt console

cp target/scala-2.10/uns2_2.10-1.0.jar /opt/akka-2.2.3/deploy/
cp extlib/* /opt/akka-2.2.3/lib/akka/

/opt/akka-2.2.3/bin/akka com.wcs.uns.Bootstrapper
