#!/bin/bash

docker login --username USERNAME --password TOKEN

cd client

npm install

npm run build:prod 

docker build -t wichtr/private:visualization-client-latest -f Dockerfile_Production .
docker push wichtr/private:visualization-client-latest

cd ../server

docker build -t wichtr/private:visualization-server-latest -f Dockerfile_Production .
docker push wichtr/private:visualization-server-latest
