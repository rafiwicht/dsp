

class FilterModule(object):
    def filters(self):
        return {
            'map_str_format': self.map_str_format
        }

    def map_str_format(self, elements: list, str_format: str, fields: list) -> list:
        result_list = []
        for e in elements:
            parameters = [e[f] for f in fields]
            result_list.append(str_format.format(*parameters))
        return result_list
