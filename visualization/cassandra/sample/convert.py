import json
from datetime import datetime as dt


END = '\n'

with open('data.txt', 'r') as f:
    content = f.read()

content = content.split('\n')[2:]
content = [e.split('|') for e in content]
content = [{'date': e[0].strip(), 'timestamp': int(dt.strptime(e[1].strip(), '%Y-%m-%d %H:%M:%S.%f%z').timestamp() * 1000),
            'value': int(e[2].strip())} for e in content]

with open('data.json', 'w') as f:
    json.dump(content, f)

with open('../init.cql', 'w') as f:
    f.write(
        "CREATE KEYSPACE IF NOT EXISTS rbd WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };" + END)
    f.write(
        "CREATE TABLE rbd.malicious (date date,timestamp timestamp,value int,PRIMARY KEY (date, timestamp)) WITH CLUSTERING ORDER BY (timestamp ASC) AND bloom_filter_fp_chance = 0.01 AND caching = {'keys': 'ALL', 'rows_per_partition': 'NONE'} AND comment = '' AND compaction = {'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy', 'max_threshold': '32', 'min_threshold': '4'} AND compression = {'chunk_length_in_kb': '64', 'class': 'org.apache.cassandra.io.compress.LZ4Compressor'} AND crc_check_chance = 1.0 AND dclocal_read_repair_chance = 0.1 AND default_time_to_live = 0 AND gc_grace_seconds = 864000 AND max_index_interval = 2048 AND memtable_flush_period_in_ms = 0 AND min_index_interval = 128 AND read_repair_chance = 0.0 AND speculative_retry = '99PERCENTILE';" + END)
    for e in content:
        f.write("INSERT INTO rbd.malicious JSON '" +
                json.dumps(e) + "';" + END)
