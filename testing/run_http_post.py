#!/usr/bin/python
from __future__ import unicode_literals
import argparse
import json
import requests
import io
import urllib
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
        with open(json_file, 'r') as jf:
            txt = jf.read()
    return json.loads(txt)


def write_data(data, out_file):
    with io.open(out_file, 'w', encoding='utf-8') as f:
        f.write(data)


def extract_filename(header):
    pattern = 'filename='
    n = header.find(pattern)
    if n != -1:
        return header[n + len(pattern):]
    else:
        return '[no name]'


arg_parser = argparse.ArgumentParser(
    description='It performs HTTP POST request with json parameter')
arg_parser.add_argument(nargs=1, dest='url', help='Requested URL')
arg_parser.add_argument('-j', dest='json_file', help='File with json')
arg_parser.add_argument('-o', dest='out_file', help='File to save response')
arg_parser.add_argument('-e', dest='headers', help='File with request headers')

args = arg_parser.parse_args()
url, payload, out_file = args.url[0], read_json(args.json_file), args.out_file

headers = {
    'content-type': 'application/json',
}
if args.headers:
    headers.update(read_json(args.headers))

print 'Performing HTTP request to %s ...' % url
response = requests.post(url, json=payload, headers=headers)
if response.ok:
    print 'Ok'
    print 'headers:'
    print response.headers
    if response.headers['content-type'] == 'application/octet-stream':
        filename = extract_filename(response.headers['content-disposition'])
        filename = urllib.unquote(filename).decode('utf8').strip('"')
        with open(filename, 'wb') as f:
            for chunk in response.iter_content(1024):
                f.write(chunk)
        print "file '%s' was received" % filename
    else:
        response_text = ''
        try:
            response_text = unicode(json.dumps(response.json(), sort_keys=True,
                                               indent=4, ensure_ascii=False))
        except JSONDecodeError:
            response_text = unicode(response.text)

        if out_file is None:
            print 'response:'
            print response_text
        else:
            write_data(response_text, out_file)
            print 'response has been saved into %s' % out_file
else:
    print 'Error %s: %s' % (response.status_code, response.reason)
