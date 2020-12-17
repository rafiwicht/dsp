from datetime import datetime, timezone
import random
import uuid
import time
import sys
import yaml


class Generator:

    def generate_logs(self) -> None:

        latency_levels = {
            1: [1, 2],
            2: [2, 4],
            3: [4, 16],
            4: [16, 500]
        }

        intensity_levels = {
            1: [1000, 2000],
            2: [200, 1000],
            3: [100, 200],
            4: [20, 100]
        }

        while True:

            for mode in self.config['modes']:

                for i in range(mode['times']):
                    latency = latency_levels.get(mode['latency_level'])
                    intensity = intensity_levels.get(mode['intensity_level'])

                    self.write_logs(random.randrange(latency[0], latency[1]) / 1000)
                    time.sleep(random.randrange(intensity[0], intensity[1]) / 1000)

    def write_logs(self, latency: float) -> None:
        own_client = random.choices([True, False], weights=[0.7, 0.3])[0]
        user = str(uuid.uuid4())
        resource = random.choice(["/product", "/catalog", "/cart", "/extension"])

        append_id = random.choice([True, False])
        if append_id:
            resource += "/" + str(random.randint(1, 219743))

        with open(self.config['webserver'], 'a') as log_file:
            log_file.write(self.get_webserver_log(user, resource, own_client))

        time.sleep(latency)

        if own_client:
            with open(self.config['client'], 'a') as log_file:
                log_file.write(self.get_client_log(user, resource))

    @staticmethod
    def get_webserver_log(user: str, resource: str, java: bool) -> str:
        ip = '.'.join(str(random.randint(0, 255)) for i in range(4))

        timestamp = datetime.utcnow().replace(tzinfo=timezone.utc).astimezone(tz=None)
        timestamp_str = timestamp.strftime('%-d/%b/%Y:%H:%M:%S.%f')[:-3]
        timestamp_str = timestamp_str + timestamp.strftime(' %z')

        method = random.choices(["GET", "POST"], weights=[0.8, 0.2])[0] if not resource.split('/')[-1].isdigit() else random.choices(["GET", "PUT", "DELETE"], weights=[0.6, 0.3, 0.1])[0]
        status_code = "201" if method == "POST" else random.choices(["200", "400", "403", "404"], weights=[0.85, 0.05, 0.05, 0.05])[0]
        response_bytes = str(random.randint(2367, 5124))

        redirect = "" if java else random.choice([
            "www.bing.com",
            "www.yahoo.com",
            "http://www.buttercupgames.com/category",
            "http://www.buttercupgames.com/cart",
            "http://www.buttercupgames.com/product"])

        client = "Java HTTP Client/v1.23.0" if java else random.choice([
            "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-GB; rv:1.8.1.6) Gecko/20070725 Firefox/2.0.0.6",
            "Googlebot/2.1 (http://www.googlebot.com/bot.html)",
            "Mozilla/5.0 (iPad; CPU OS 5_1_1 like Mac OS X) AppleWebKit/534.46 (KHTML, like Gecko) Version/5.1 Mobile/9B206 Safari/7534.48.3",
            "Mozilla/4.0 (compatible; MSIE 7.0; Windows NT 5.1; .NET CLR 2.0.50727; .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729; InfoPath.1; .NET4.0C; .NET4.0E; MS-RTC LM 8)",
            "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/536.5 (KHTML, like Gecko) Chrome/19.0.1084.46 Safari/536.5",
            "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0; BOIE9;ENUS),"
            "Opera/9.20 (Windows NT 6.0; U; en)",
            "Mozilla/5.0 (iPad; U; CPU OS 4_3_5 like Mac OS X; en-us) AppleWebKit/533.17.9 (KHTML, like Gecko) Version/5.0.2 Mobile/8L1 Safari/6533.18.5",
            "Curl/7.6800",
            "Wget/1.20.3"])

        return '{0} - {1} [{2}] "{3} {4} {5}" {6} {7} "{8}" "{9}"\n'.format(
            ip,
            user,
            timestamp_str,
            method,
            resource,
            "HTTP/1.1",
            status_code,
            response_bytes,
            redirect,
            client)

    @staticmethod
    def get_client_log(user: str, resource: str) -> str:
        timestamp = datetime.now().strftime('%d.%m.%Y %H:%M:%S.%f')[:-3]

        return '{0} {1} {2} {3}: user={4} resource={5}\n'.format(
            timestamp,
            "com.rwicht.java.client",
            "main",
            "INFO",
            user,
            resource)

    def __init__(self, config_file: str):
        self.config = yaml.load(open(config_file), Loader=yaml.SafeLoader)


generator = Generator(sys.argv[1])
generator.generate_logs()
