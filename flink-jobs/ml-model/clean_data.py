import json
import random


with open('data/data.txt', 'r') as fin:
    data_strings = []
    for line in fin:
        if not line.startswith('%'):
            data_strings.append(line.rstrip('\n'))

data = json.loads('[' + ','.join(data_strings) + ']')

for e in data:
    risk_score = 0
    if e['method'] == 'delete':
        risk_score += 3
    if e['httpCode'] >= 400 and e['httpCode'] < 500:
        risk_score += 5
    if e['userAgent'] != 'Java HTTP Client/v1.23.0':
        risk_score += 1

    if random.randint(1,10) * risk_score > 10:
        e['malicious'] = 1
    else:
        e['malicious'] = 0
        

with open('data/data.json', 'w') as outfile:
    outfile.write(json.dumps(data, indent=4))