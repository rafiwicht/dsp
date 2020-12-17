#!/bin/bash

. /opt/venv/bin/activate && python /opt/generator.py /etc/log-generator-config/config.yml &> /dev/null &
/bin/bash
