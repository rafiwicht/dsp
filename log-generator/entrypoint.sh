#!/bin/bash

. /opt/venv/bin/activate && python /opt/generator.py /etc/log-generator-config/config.yml &> /dev/null &
/usr/share/logstash/bin/logstash -f /etc/logstash-config/ --path.settings /etc/logstash
