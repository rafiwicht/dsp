import time
from flask import Flask, request
from flask_cors import CORS
from data import CassandraQuery
import sys
import os

DEBUG = True

if 'DEBUG' in os.environ:
    DEBUG = (os.environ['DEBUG'] == 'True')

app = Flask(__name__)
CORS(app)
data = CassandraQuery()

@app.route('/malicious')
def get_malicious_data():
    start = request.args.get('start')
    end = request.args.get('end')
    return data.malicious_query(int(start), int(end))

@app.route('/latency')
def get_latency_data():
    start = request.args.get('start')
    end = request.args.get('end')
    return data.latency_query(int(start), int(end))

@app.route('/popular')
def get_popular_data():
    start = request.args.get('start')
    end = request.args.get('end')
    return data.popular_query(int(start), int(end))


if __name__ == "__main__":
    app.run(host='0.0.0.0', debug=DEBUG, port=5000)