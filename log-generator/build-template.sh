#!/bin/bash

docker login --username USERNAME --password PASSWORD

docker build -t wichtr/private:loggen-latest .
docker push  wichtr/private:loggen-latest
