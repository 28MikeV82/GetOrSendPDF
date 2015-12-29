#!/usr/bin/python
from __future__ import unicode_literals
import argparse
import json
import requests
import io
from simplejson.scanner import JSONDecodeError


def read_json(json_file):
    txt = None
    if json_file is None:
        j = []
        while True:
            i = raw_input('> ')
            if not i:
                break
            j.append(i)
        txt = '\n'.join(j)
    else:
        with open(args.json_file, 'r') as jf:
            txt = jf.read()
    return json.loads(txt)


def write_data(data, out_file):
    with io.open(out_file, 'w', encoding='utf-8') as f:
        f.write(data)


arg_parser = argparse.ArgumentParser(
    description='It performs HTTP POST request with json parameter')
arg_parser.add_argument(nargs=1, dest='url', help='Requested URL')
arg_parser.add_argument('-j', dest='json_file', help='File with json')
arg_parser.add_argument('-o', dest='out_file', help='File to save response')

args = arg_parser.parse_args()
url, payload, out_file = args.url[0], read_json(args.json_file), args.out_file

print 'Performing HTTP request to %s ...' % url
headers = {'content-type': 'application/json'}
r = requests.post(url, json=payload, headers=headers)
if r.status_code == requests.codes.ok:
    response = ''
    try:
        response = unicode(json.dumps(r.json(), sort_keys=True,
                                      indent=4, ensure_ascii=False))
    except JSONDecodeError:
        response = r.text

    if out_file is None:
        print 'Ok, response:'
        print response
    else:
        write_data(response, out_file)
        print 'Ok, response has been saved into %s' % out_file
else:
    print 'Error %s: %s' % (r.status_code, r.reason)
