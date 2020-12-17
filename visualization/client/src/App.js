import React, { useState } from 'react';
import axios from 'axios';
import DateTimePicker from 'react-datetime-picker';

import './App.css';
import { TimeInput } from './hooks/timeInput';

import environment from './config/config';
import LineChart from './components/LineChart';


axios.defaults.baseURL = environment.baseUrl;

function App() {

    const { value: startTime, bind: bindStartTime, reset: resetStartTime } = TimeInput(new Date());
    const { value: endTime, bind: bindEndTime, reset: resetEndTime } = TimeInput(new Date());
    const [maliciousData, setMaliciousData] = useState([])
    const [latencyData, setLatencyData] = useState([])
    const [popularData, setPopularData] = useState([])

    const handleSubmit = (evt) => {
        evt.preventDefault();
        axios.get('/malicious?start=' + startTime.valueOf().toString() + '&end=' + endTime.valueOf().toString())
            .then(function (response) {
                setMaliciousData([{
                    id: 'malicious',
                    color: 'hsl(266, 70%, 50%)',
                    data: response['data']['malicious']
                }]);
                resetStartTime();
                resetEndTime();
            });
        axios.get('/latency?start=' + startTime.valueOf().toString() + '&end=' + endTime.valueOf().toString())
            .then(function (response) {
                const data = response['data'];
                setLatencyData([{
                    id: 'min',
                    color: 'hsl(266, 70%, 50%)',
                    data: data['min']
                }, {
                    id: 'max',
                    color: 'hsl(210, 70%, 50%)',
                    data: data['max']
                }, {
                    id: 'avg',
                    color: 'hsl(81, 70%, 50%)',
                    data: data['avg']
                }]);
                resetStartTime();
                resetEndTime();
            });
        axios.get('/popular?start=' + startTime.valueOf().toString() + '&end=' + endTime.valueOf().toString())
            .then(function (response) {
                const data = response['data'];
                const keys = Object.keys(data);
                const len = keys.length;
                const colorDistance = 360 / len;
                let array = [];
                for (let i = 0; i < len; i++) {
                    array.push({
                        id: keys[i],
                        color: 'hsl(' + (i * colorDistance).toString() + ', 70%, 50%)',
                        data: data[keys[i]]
                    })
                }

                setPopularData(array);
                resetStartTime();
                resetEndTime();
            });
    };

    return (
        <div className="App">
            <form onSubmit={handleSubmit}>
                <label>
                    Start Time:
                    <DateTimePicker {...bindStartTime} />
                </label>
                <label>
                    End Time:
                    <DateTimePicker {...bindEndTime} />
                </label>
                <input type="submit" value="Submit" />
            </form>
            <LineChart 
                data={maliciousData} 
                legendX='time'
                legendY='count' />
            <LineChart  
                data={popularData} 
                legendX='time'
                legendY='count' />
            <LineChart  
                data={latencyData} 
                legendX='time'
                legendY='latency in ms' />
        </div>

    );
}

export default App;
