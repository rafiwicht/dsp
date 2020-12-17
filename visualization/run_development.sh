#!/bin/bash

cd client
npm install
cd ..

docker-compose down

docker-compose up -d

cd client/

yarn start