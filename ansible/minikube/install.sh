#!/bin/bash

kubectl create namespace kafka
kubectl apply -f 'https://strimzi.io/install/latest?namespace=kafka' -n kafka
kubectl apply -f kafka-cluster.yml -n kafka 
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

kubectl apply -f regcred.yml
# ansible-playbook flink-jobs.yml
