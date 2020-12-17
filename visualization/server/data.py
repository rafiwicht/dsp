from cassandra.cluster import Cluster
from cassandra.auth import PlainTextAuthProvider
from datetime import datetime as dt
import sys
import os

USE_AUTH = False

CASSANDRA_SERVER = os.environ['CASSANDRA_SERVER']
CASSANDRA_PORT = os.environ['CASSANDRA_PORT']

if 'CASSANDRA_USER' in os.environ:
    USE_AUTH = True
    CASSANDRA_USER = os.environ['CASSANDRA_USER']
    CASSANDRA_PASSWORD = os.environ['CASSANDRA_PASSWORD']


class CassandraQuery:
    def malicious_query(self, start_time: int, end_time: int) -> list:
        query = "SELECT timestamp, value FROM malicious.result WHERE timestamp <= {0} AND timestamp >= {1} ALLOW FILTERING;".format(
            str(end_time), str(start_time))
        rows = self.session.execute(query)
        return {'malicious': [{'x': e.timestamp.timestamp(), 'y': e.value} for e in rows]}

    def popular_query(self, start_time: int, end_time: int) -> list:
        query = "SELECT path, timestamp, value FROM popular.result WHERE timestamp <= {0} AND timestamp >= {1} ALLOW FILTERING;".format(
            str(end_time), str(start_time))
        rows = self.session.execute(query)

        result = {}
        for row in rows:
            if row.path in result:
                result[row.path].append({'x': row.timestamp.timestamp(), 'y': row.value})
            else:
                result[row.path] = [{'x': row.timestamp.timestamp(), 'y': row.value}]
        return result

    def latency_query(self, start_time: int, end_time: int) -> list:
        query = "SELECT timestamp, min, max, avg FROM latency.result WHERE timestamp <= {0} AND timestamp >= {1} ALLOW FILTERING;".format(
            str(end_time), str(start_time))
        rows = self.session.execute(query)
        
        result = {'min': [], 'max': [], 'avg': []}
        for row in rows:
            ts = row.timestamp.timestamp()
            result['min'].append({'x': ts, 'y': row.min})
            result['max'].append({'x': ts, 'y': row.max})
            result['avg'].append({'x': ts, 'y': row.avg})
        return result
    
    def __init__(self):
        if USE_AUTH:
            auth_provider = PlainTextAuthProvider(username=CASSANDRA_USER, password=CASSANDRA_PASSWORD)
            self.session = Cluster(CASSANDRA_SERVER.split(','), port=CASSANDRA_PORT, auth_provider=auth_provider).connect()
        else:
            self.session = Cluster(CASSANDRA_SERVER.split(','), port=CASSANDRA_PORT).connect()
