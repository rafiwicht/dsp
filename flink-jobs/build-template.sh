#!/bin/bash

declare -a helper=( "helper" )
declare -a flink_jobs=( "access-parser-java" "client-parser-java" "malicious-access" "latency-streaming" "popular-pages" )

docker login --username USERNAME --password TOKEN

for i in "${helper[@]}"
do 
    mvn clean install -f ./$i/pom.xml
done

for i in "${flink_jobs[@]}"
do 
    mvn clean package -f ./$i/
    if [ ! -d "../$i" ] 
    then
        mkdir ../$i
    fi 
    cp -f Dockerfile ../$i/Dockerfile
    cp -f $i/target/$i-1.0.0.jar ../$i/$i-1.0.0.jar
    sed -i "s/JAR/$i/g" ../$i/Dockerfile
    docker build -t wichtr/private:$i-latest ../$i/
    docker push wichtr/private:$i-latest
done
