#!/bin/bash

kubectl create namespace kafka
kubectl apply -f 'https://strimzi.io/install/latest?namespace=kafka^' -n kafka
kubectl apply -f https://strimzi.io/examples/latest/kafka/kafka-persistent-single.yaml -n kafka 
kubectl apply -f kafka-topic.yml -n kafka

cd flink-on-k8s-operator
helm repo add flink-operator-repo https://googlecloudplatform.github.io/flink-on-k8s-operator/
helm install flink-operator ./helm-chart/flink-operator/ --set operatorImage.name=gcr.io/flink-operator/flink-operator:latest
cd ..

kubectl create namespace log-generator
kubectl apply -f regcred.yml -n log-generator
kubectl apply -f lg-gen-config.yml -n log-generator
kubectl apply -f lg-logstash-config.yml -n log-generator
kubectl apply -f lg-gen.yml -n log-generator

kubectl apply -f storage-class.yml
kubectl apply -f local-storage.yml

kubectl apply -f cass-operator-manifests.yml
kubectl apply -f cassandra-dc1.yml -n cass-operator

kubectl -n cass-operator exec -c cassandra cluster1-dc1-default-sts-0 -- cqlsh -u cluster1-superuser -p uGs9xxeDyOqftwcjuJm7CAeOWoLp1Tk8YztV3GdLLnzM8KgcOGIZrw --execute="ALTER KEYSPACE system_auth WITH REPLICATION = { 'class' : 'NetworkTopologyStrategy', 'dc1' : 1 };"
kubectl -n cass-operator exec -c cassandra cluster1-dc1-default-sts-0 -- cqlsh -u cluster1-superuser -p uGs9xxeDyOqftwcjuJm7CAeOWoLp1Tk8YztV3GdLLnzM8KgcOGIZrw --execute="CONSISTENCY QUORUM"

kubectl -n cass-operator exec -c cassandra cluster1-dc1-default-sts-0 -- cqlsh -u cluster1-superuser -p uGs9xxeDyOqftwcjuJm7CAeOWoLp1Tk8YztV3GdLLnzM8KgcOGIZrw --execute="CREATE KEYSPACE IF NOT EXISTS malicious WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };"
kubectl -n cass-operator exec -c cassandra cluster1-dc1-default-sts-0 -- cqlsh -u cluster1-superuser -p uGs9xxeDyOqftwcjuJm7CAeOWoLp1Tk8YztV3GdLLnzM8KgcOGIZrw --execute="CREATE TABLE IF NOT EXISTS malicious.result (date date,timestamp timestamp,value int,PRIMARY KEY (date, timestamp)) WITH CLUSTERING ORDER BY (timestamp ASC);"

kubectl -n cass-operator exec -c cassandra cluster1-dc1-default-sts-0 -- cqlsh -u cluster1-superuser -p uGs9xxeDyOqftwcjuJm7CAeOWoLp1Tk8YztV3GdLLnzM8KgcOGIZrw --execute="CREATE KEYSPACE IF NOT EXISTS latency WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };"
kubectl -n cass-operator exec -c cassandra cluster1-dc1-default-sts-0 -- cqlsh -u cluster1-superuser -p uGs9xxeDyOqftwcjuJm7CAeOWoLp1Tk8YztV3GdLLnzM8KgcOGIZrw --execute="CREATE TABLE IF NOT EXISTS latency.result (date date,timestamp timestamp,min bigint,max bigint,avg double,PRIMARY KEY (date, timestamp)) WITH CLUSTERING ORDER BY (timestamp ASC);"

kubectl -n cass-operator exec -c cassandra cluster1-dc1-default-sts-0 -- cqlsh -u cluster1-superuser -p uGs9xxeDyOqftwcjuJm7CAeOWoLp1Tk8YztV3GdLLnzM8KgcOGIZrw --execute="CREATE KEYSPACE IF NOT EXISTS popular WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };"
kubectl -n cass-operator exec -c cassandra cluster1-dc1-default-sts-0 -- cqlsh -u cluster1-superuser -p uGs9xxeDyOqftwcjuJm7CAeOWoLp1Tk8YztV3GdLLnzM8KgcOGIZrw --execute="CREATE TABLE IF NOT EXISTS popular.result (path text,timestamp timestamp,value int,PRIMARY KEY (path, timestamp)) WITH CLUSTERING ORDER BY (timestamp ASC);"
 
#kubectl apply -f regcred.yml
#ansible-playbook flink-jobs.yml
