const environment = {
    baseUrl: "http://localhost:5000"
};

if (process.env.REACT_APP_ENV === "production" ) {
    environment.baseUrl = "http://rbd.rwicht.ch:31012";
}

export default environment;