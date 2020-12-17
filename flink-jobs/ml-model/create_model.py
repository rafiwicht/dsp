from sklearn.naive_bayes import GaussianNB, MultinomialNB, BernoulliNB, ComplementNB, CategoricalNB
from sklearn.model_selection import train_test_split
import pandas as pd
import numpy as np
import json

# Load JSON
with open('data/data.json') as json_file:
    data = json.load(json_file)

# Create df and filter fields
data_df = pd.DataFrame(data)[['timestamp', 'method', 'httpCode', 'userAgent', 'malicious']]

# Tokenize for native Bayes
for field in ['method', 'userAgent']:
    data_df[field] = data_df[field].apply(lambda x: int.from_bytes(x[:4].encode(), 'big') if x != "" else 0)
    

# Create test and validation data
X_train, X_test, y_train, y_test = train_test_split(data_df[['timestamp', 'method', 'httpCode', 'userAgent']], data_df['malicious'] , test_size=0.1)

print(X_train)

# Train model
model = CategoricalNB().fit(X_train, y_train)

# Test model
predicted = model.predict(X_test)
print(sum(predicted))
print(np.mean(predicted == y_test))
