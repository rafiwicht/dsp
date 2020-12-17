from sklearn.tree import DecisionTreeClassifier
from sklearn.model_selection import train_test_split
import pandas as pd
import numpy as np
import json
import sklearn_json as skljson
from sklearn_pandas import DataFrameMapper
from sklearn2pmml import sklearn2pmml
from sklearn2pmml.pipeline import PMMLPipeline



# Load JSON
with open('data/data.json') as json_file:
    data = json.load(json_file)

# Create df and filter fields
data_df = pd.DataFrame(data)[['timestamp', 'method', 'httpCode', 'userAgent', 'malicious']]

# Tokenize for native Bayes
for field in ['method', 'userAgent']:
    data_df[field] = data_df[field].apply(lambda x: int.from_bytes(x[0:3].encode(), 'big') if x != '' else 0)


# Create test and validation data
X_train, X_test, y_train, y_test = train_test_split(data_df[['timestamp', 'method', 'httpCode', 'userAgent']], data_df['malicious'] , test_size=0.1)

# Train model
model = DecisionTreeClassifier().fit(X_train, y_train)

# Test model
predicted = model.predict(X_test)
print(sum(predicted))
print(np.mean(predicted == y_test))

# Save model
skljson.to_json(model, 'output/model.json')

# Save to pmml

pipeline = PMMLPipeline([
	("classifier", DecisionTreeClassifier())
])

pipeline.fit(X_train, y_train)

sklearn2pmml(pipeline, "output/model.pmml")
