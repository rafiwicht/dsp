import { useState } from 'react';

export const TimeInput = initialValue => {
    const [value, setValue] = useState(initialValue);

    return {
        value,
        setValue,
        reset: () => setValue(new Date()),
        bind: {
            value: value,
            onChange: date => {
                setValue(date);
            },
            maxDate: new Date(),
            format: "y-MM-dd HH:mm:ss"
        }
    };
};
