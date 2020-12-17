FROM centos:latest
LABEL ch.post.it.description="Container for logstash kafka log-generator" \
    ch.post.it.maintainer.email="rafi.wicht139@gmail.com" \
    ch.post.it.notification.email="" \
    ch.post.it.app.name="Logstash-Kafka-Log-generator" \
    ch.post.it.app.version="1.0.0" \
    ch.post.it.app.docs="" \
    ch.post.it.project.shortname=""

COPY requirements.txt /opt/
COPY entrypoint.sh /opt/
COPY logstash.repo /etc/yum.repos.d/

RUN rpm --import https://artifacts.elastic.co/GPG-KEY-elasticsearch
RUN yum -y install java-11-openjdk-devel ruby logstash python3 python3-virtualenv python3-pip

RUN /usr/share/logstash/bin/logstash-plugin install logstash-input-file

RUN python3 -m venv /opt/venv --system-site-packages \
  && . /opt/venv/bin/activate \
  && pip install -r /opt/requirements.txt

RUN mkdir /opt/log-generator \
  && mkdir /opt/logstash
RUN chmod +x /opt/entrypoint.sh

COPY generator.py /opt/

CMD /opt/entrypoint.sh
