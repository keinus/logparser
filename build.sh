#!/bin/bash
./gradlew build
sudo docker build . --tag=keinus/logparser:latest
